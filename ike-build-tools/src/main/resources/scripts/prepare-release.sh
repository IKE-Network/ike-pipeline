#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────
# prepare-release.sh — Transition from SNAPSHOT to release version
# ────────────────────────────────────────────────────────────────────
#
# Usage:
#   ./prepare-release.sh <release-version> [--dry-run]
#
# Example:
#   ./prepare-release.sh 1
#   ./prepare-release.sh 1 --dry-run
#
# Prerequisites:
#   - On main branch (or specify --allow-branch to override)
#   - Working tree clean
#   - JDK and Maven wrapper (mvnw) available
#
# What it does:
#   1. Validates prerequisites (branch, clean worktree)
#   2. Logs build environment for audit/traceability
#   3. Creates release/<version> branch
#   4. Sets root POM version (Maven 4.1.0 implicit inheritance)
#   5. Builds and verifies (mvnw clean verify)
#   6. Commits the version change and tags v<version>
#   7. Deploys artifacts to Nexus (mvnw deploy -DskipTests)
#   8. Pushes tag and creates GitHub Release (via gh CLI)
#   9. Merges release branch back to main, pushes
#  10. Deletes the local release branch
#
# After completion, run post-release.sh to bump to next SNAPSHOT.
# ────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Resolve project root and Maven wrapper ────────────────────────
# Maven 4.1.0 features (implicit version inheritance, <subprojects>)
# require Maven 4.x. The wrapper ensures the correct version.
GIT_ROOT="$(git rev-parse --show-toplevel)"
if [[ -x "${GIT_ROOT}/mvnw" ]]; then
    MVN="${GIT_ROOT}/mvnw"
else
    echo "ERROR: mvnw not found at ${GIT_ROOT}/mvnw"
    echo "       Maven 4.x wrapper is required for this project."
    exit 1
fi
ROOT_POM="${GIT_ROOT}/pom.xml"

# ── Defaults ──────────────────────────────────────────────────────
DRY_RUN=false
RELEASE_VERSION=""
ALLOW_BRANCH=""
SKIP_VERIFY=false

# ── Parse arguments ───────────────────────────────────────────────
usage() {
    cat <<EOF
Usage: $0 <release-version> [options]

  release-version        Version to release (e.g., 1)

Options:
  --dry-run              Show what would happen without making changes
  --allow-branch <name>  Allow release from a branch other than main
  --skip-verify          Skip 'mvnw clean verify' (use if you just ran it)
  --help, -h             Show this help
EOF
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)        DRY_RUN=true; shift ;;
        --allow-branch)   ALLOW_BRANCH="$2"; shift 2 ;;
        --skip-verify)    SKIP_VERIFY=true; shift ;;
        --help|-h)        usage ;;
        -*)               echo "Unknown option: $1"; usage ;;
        *)                RELEASE_VERSION="$1"; shift ;;
    esac
done

if [[ -z "${RELEASE_VERSION}" ]]; then
    echo "ERROR: Release version is required."
    usage
fi

# Reject SNAPSHOT versions
if [[ "${RELEASE_VERSION}" == *-SNAPSHOT ]]; then
    echo "ERROR: Release version must not contain -SNAPSHOT."
    exit 1
fi

# ── Validate prerequisites ────────────────────────────────────────
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
EXPECTED_BRANCH="${ALLOW_BRANCH:-main}"

if [[ "${CURRENT_BRANCH}" != "${EXPECTED_BRANCH}" ]]; then
    echo "ERROR: Must be on '${EXPECTED_BRANCH}' branch (currently on '${CURRENT_BRANCH}')."
    echo "       Use --allow-branch ${CURRENT_BRANCH} to override."
    exit 1
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "ERROR: Working tree is not clean."
    echo "       Commit or stash changes before releasing."
    exit 1
fi

RELEASE_BRANCH="release/${RELEASE_VERSION}"

# Check if release branch already exists
if git rev-parse --verify "${RELEASE_BRANCH}" >/dev/null 2>&1; then
    echo "ERROR: Branch '${RELEASE_BRANCH}' already exists locally."
    exit 1
fi

if git ls-remote --exit-code --heads origin "${RELEASE_BRANCH}" >/dev/null 2>&1; then
    echo "ERROR: Branch '${RELEASE_BRANCH}' already exists on remote."
    exit 1
fi

# Extract current version from root POM for audit trail display.
# Cannot use help:evaluate — Maven 3 plugins choke on 4.1.0 model.
OLD_VERSION=$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' "${ROOT_POM}" | head -1)
if [[ -z "${OLD_VERSION}" ]]; then
    echo "ERROR: Could not extract current version from ${ROOT_POM}"
    exit 1
fi

# ── Build environment audit ───────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  RELEASE PARAMETERS"
echo "═══════════════════════════════════════════════════════════"
echo "  Version:       ${OLD_VERSION} → ${RELEASE_VERSION}"
echo "  Source branch: ${CURRENT_BRANCH}"
echo "  Release branch:${RELEASE_BRANCH}"
echo "  Tag:           v${RELEASE_VERSION}"
echo "  Skip verify:   ${SKIP_VERIFY}"
echo "  Dry run:       ${DRY_RUN}"
echo ""
echo "  BUILD ENVIRONMENT"
echo "  ─────────────────────────────────────────────────────"
echo "  Date:          $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo "  User:          $(whoami)@$(hostname)"
echo "  Git commit:    $(git rev-parse --short HEAD)"
echo "  Git root:      ${GIT_ROOT}"
echo "  Maven wrapper: ${MVN}"
echo "  Maven version: $("${MVN}" --version 2>&1 | head -1)"
echo "  Java version:  $(java -version 2>&1 | head -1)"
echo "  OS:            $(uname -mrs)"
echo "═══════════════════════════════════════════════════════════"
echo ""

if [[ "${DRY_RUN}" == "true" ]]; then
    echo "[DRY RUN] Would create branch: ${RELEASE_BRANCH}"
    echo "[DRY RUN] Would set version: ${OLD_VERSION} → ${RELEASE_VERSION}"
    echo "[DRY RUN] Would run: mvnw clean verify -B"
    echo "[DRY RUN] Would commit, tag v${RELEASE_VERSION}"
    echo "[DRY RUN] Would deploy to Nexus: mvnw deploy -B -DskipTests"
    echo "[DRY RUN] Would push tag and create GitHub Release"
    echo "[DRY RUN] Would merge ${RELEASE_BRANCH} to main and push"
    exit 0
fi

# ── Create release branch ─────────────────────────────────────────
echo "» Creating branch: ${RELEASE_BRANCH}"
git checkout -b "${RELEASE_BRANCH}"

# ── Set version ───────────────────────────────────────────────────
# Maven 4.1.0 implicit version inheritance: only the root POM declares
# <version>. All submodules inherit it automatically. We sed the single
# version in the root POM. Neither versions:set nor help:evaluate work
# because the versions-maven-plugin uses the Maven 3 model API internally
# and cannot parse modelVersion 4.1.0 (fails with 'parent.version is
# missing'). This is a known limitation of Maven 3 era plugins running
# under Maven 4.
echo "» Setting version: ${OLD_VERSION} → ${RELEASE_VERSION}"
sed -i '' "s|<version>${OLD_VERSION}</version>|<version>${RELEASE_VERSION}</version>|" "${ROOT_POM}"

# ── Verify build ──────────────────────────────────────────────────
if [[ "${SKIP_VERIFY}" == "false" ]]; then
    echo "» Building and verifying..."
    "${MVN}" clean verify -B
else
    echo "» Skipping verify (--skip-verify)"
fi

# ── Commit ────────────────────────────────────────────────────────
echo "» Committing version change..."
git add -A
git commit -m "release: set version to ${RELEASE_VERSION}"

# ── Tag ───────────────────────────────────────────────────────────
echo "» Tagging: v${RELEASE_VERSION}"
git tag -a "v${RELEASE_VERSION}" -m "Release ${RELEASE_VERSION}"

# ── Deploy to Nexus ───────────────────────────────────────────────
echo "» Deploying to Nexus..."
"${MVN}" deploy -B -DskipTests

# ── Push tag (needed before gh release create) ────────────────────
echo "» Pushing tag v${RELEASE_VERSION}..."
git push origin "v${RELEASE_VERSION}"

# ── Create GitHub Release ─────────────────────────────────────────
if command -v gh &>/dev/null; then
    echo "» Creating GitHub Release..."
    gh release create "v${RELEASE_VERSION}" \
        --title "${RELEASE_VERSION}" \
        --generate-notes \
        --verify-tag
else
    echo "⚠ gh CLI not found — skipping GitHub Release creation."
    echo "  Install: https://cli.github.com"
    echo "  Then run manually: gh release create v${RELEASE_VERSION} --title ${RELEASE_VERSION} --generate-notes"
fi

# ── Merge back to main and push ───────────────────────────────────
echo "» Merging ${RELEASE_BRANCH} back to main..."
git checkout main
git merge --no-ff "${RELEASE_BRANCH}" -m "merge: release ${RELEASE_VERSION}"
git push origin main

# ── Clean up release branch ───────────────────────────────────────
echo "» Cleaning up release branch..."
git branch -d "${RELEASE_BRANCH}"

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  Release ${RELEASE_VERSION} complete."
echo ""
echo "  ✓ Tagged: v${RELEASE_VERSION}"
echo "  ✓ Deployed to Nexus"
echo "  ✓ GitHub Release created"
echo "  ✓ Merged to main"
echo ""
echo "  Next: run post-release.sh to bump to next SNAPSHOT:"
echo "    ./post-release.sh <next-version>-SNAPSHOT"
echo "═══════════════════════════════════════════════════════════"
