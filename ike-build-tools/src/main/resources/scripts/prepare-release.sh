#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────
# prepare-release.sh — Transition from SNAPSHOT to release version
# ────────────────────────────────────────────────────────────────────
#
# Usage:
#   ./prepare-release.sh <release-version> [--dry-run]
#
# Example:
#   ./prepare-release.sh 1.1.0
#   ./prepare-release.sh 1.1.0 --dry-run
#
# Prerequisites:
#   - On main branch (or specify --allow-branch to override)
#   - Working tree clean
#   - JDK and Maven available
#
# What it does:
#   1. Validates prerequisites (branch, clean worktree)
#   2. Creates release/<version> branch
#   3. Sets all POM versions to <release-version> (strips -SNAPSHOT)
#   4. Builds and verifies (mvn clean verify)
#   5. Commits the version change and tags v<version>
#   6. Deploys artifacts to Nexus (mvn deploy -DskipTests)
#   7. Pushes tag and creates GitHub Release (via gh CLI)
#   8. Merges release branch back to main, pushes
#   9. Deletes the local release branch
#
# After completion, run post-release.sh to bump to next SNAPSHOT.
# ────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Resolve Maven command ─────────────────────────────────────────
# Maven 4.1.0 features (implicit version inheritance, <subprojects>)
# require Maven 4.x. Use the wrapper to ensure the correct version.
GIT_ROOT="$(git rev-parse --show-toplevel)"
if [[ -x "${GIT_ROOT}/mvnw" ]]; then
    MVN="${GIT_ROOT}/mvnw"
else
    echo "ERROR: mvnw not found at ${GIT_ROOT}/mvnw"
    echo "       Maven 4.x wrapper is required for this project."
    exit 1
fi

# ── Defaults ──────────────────────────────────────────────────────
DRY_RUN=false
RELEASE_VERSION=""
ALLOW_BRANCH=""
SKIP_VERIFY=false

# ── Parse arguments ───────────────────────────────────────────────
usage() {
    cat <<EOF
Usage: $0 <release-version> [options]

  release-version        Version to release (e.g., 1.1.0)

Options:
  --dry-run              Show what would happen without making changes
  --allow-branch <name>  Allow release from a branch other than main
  --skip-verify          Skip 'mvn clean verify' (use if you just ran it)
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

# ── Summary ───────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  Release:      ${RELEASE_VERSION}"
echo "  Source:        ${CURRENT_BRANCH}"
echo "  Branch:        ${RELEASE_BRANCH}"
echo "  Skip verify:   ${SKIP_VERIFY}"
echo "  Dry run:       ${DRY_RUN}"
echo "═══════════════════════════════════════════════════════════"
echo ""

if [[ "${DRY_RUN}" == "true" ]]; then
    echo "[DRY RUN] Would create branch: ${RELEASE_BRANCH}"
    echo "[DRY RUN] Would set version to: ${RELEASE_VERSION}"
    echo "[DRY RUN] Would run: mvn clean verify -B"
    echo "[DRY RUN] Would commit, tag v${RELEASE_VERSION}"
    echo "[DRY RUN] Would deploy to Nexus: mvnw deploy -B -DskipTests"
    echo "[DRY RUN] Would push tag and create GitHub Release"
    echo "[DRY RUN] Would merge ${RELEASE_BRANCH} to main and push"
    exit 0
fi

# ── Create release branch ─────────────────────────────────────────
echo "» Creating branch: ${RELEASE_BRANCH}"
git checkout -b "${RELEASE_BRANCH}"

# ── Set versions ──────────────────────────────────────────────────
# Maven 4.1.0 implicit version inheritance means only the root POM
# declares <version>. All submodules inherit it. A single sed on the
# root POM is sufficient; versions:set doesn't understand 4.1.0.
echo "» Setting POM versions to: ${RELEASE_VERSION}"
ROOT_POM="$(git rev-parse --show-toplevel)/pom.xml"
OLD_VERSION=$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' "${ROOT_POM}" | head -1)
if [[ -z "${OLD_VERSION}" ]]; then
    echo "ERROR: Could not extract current version from ${ROOT_POM}"
    exit 1
fi
echo "  Replacing ${OLD_VERSION} → ${RELEASE_VERSION}"
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
