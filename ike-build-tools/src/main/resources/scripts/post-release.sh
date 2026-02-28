#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────
# post-release.sh — Bump main to next SNAPSHOT after release
# ────────────────────────────────────────────────────────────────────
#
# Usage:
#   ./post-release.sh <next-version> [--dry-run]
#
# Example:
#   ./post-release.sh 2-SNAPSHOT
#
# Prerequisites:
#   - Release has been completed (prepare-release.sh)
#   - On main branch
#   - Working tree clean
#
# What it does:
#   1. Pulls latest main (to pick up release merge)
#   2. Logs build environment for audit/traceability
#   3. Sets root POM version to <next-version>
#   4. Commits and pushes
#
# ────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Resolve project root and Maven wrapper ─────────────────────────────
GIT_ROOT="$(git rev-parse --show-toplevel)"
ROOT_POM="${GIT_ROOT}/pom.xml"
if [[ -x "${GIT_ROOT}/mvnw" ]]; then
    MVN="${GIT_ROOT}/mvnw"
else
    echo "ERROR: mvnw not found at ${GIT_ROOT}/mvnw"
    echo "       Maven 4.x wrapper is required for this project."
    exit 1
fi

# ── Defaults ──────────────────────────────────────────────────────
DRY_RUN=false
NEXT_VERSION=""

# ── Parse arguments ───────────────────────────────────────────────
usage() {
    cat <<EOF
Usage: $0 <next-version> [--dry-run]

  next-version   Next development version (e.g., 2-SNAPSHOT)
  --dry-run      Show what would happen without making changes

EOF
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run) DRY_RUN=true; shift ;;
        --help|-h) usage ;;
        -*) echo "Unknown option: $1"; usage ;;
        *) NEXT_VERSION="$1"; shift ;;
    esac
done

if [[ -z "${NEXT_VERSION}" ]]; then
    echo "ERROR: Next version is required."
    usage
fi

# Enforce SNAPSHOT suffix
if [[ "${NEXT_VERSION}" != *-SNAPSHOT ]]; then
    echo "ERROR: Next version must end with -SNAPSHOT (got '${NEXT_VERSION}')."
    echo "       Example: $0 ${NEXT_VERSION}-SNAPSHOT"
    exit 1
fi

# ── Validate prerequisites ────────────────────────────────────────
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"

if [[ "${CURRENT_BRANCH}" != "main" ]]; then
    echo "ERROR: Must be on 'main' branch (currently on '${CURRENT_BRANCH}')."
    exit 1
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "ERROR: Working tree is not clean."
    echo "       Commit or stash changes first."
    exit 1
fi

# Extract current version from root POM
OLD_VERSION=$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' "${ROOT_POM}" | head -1)

# ── Build environment audit ───────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  POST-RELEASE VERSION BUMP"
echo "═══════════════════════════════════════════════════════════"
echo "  Version:       ${OLD_VERSION} → ${NEXT_VERSION}"
echo "  Branch:        ${CURRENT_BRANCH}"
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
echo "  OS:            $(uname -mrs)"
echo "═══════════════════════════════════════════════════════════"
echo ""

if [[ "${DRY_RUN}" == "true" ]]; then
    echo "[DRY RUN] Would pull latest main"
    echo "[DRY RUN] Would set version: ${OLD_VERSION} → ${NEXT_VERSION}"
    echo "[DRY RUN] Would commit and push"
    exit 0
fi

# ── Pull latest (release merge) ──────────────────────────────────
echo "» Pulling latest main..."
git pull origin main

# ── Set version ───────────────────────────────────────────────────
# Re-extract version after pull (merge may have changed it).
OLD_VERSION=$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' "${ROOT_POM}" | head -1)
echo "» Setting version: ${OLD_VERSION} → ${NEXT_VERSION}"
perl -pi -e "s|<version>${OLD_VERSION}</version>|<version>${NEXT_VERSION}</version>|" "${ROOT_POM}"

# ── Verify build ─────────────────────────────────────────────────
echo "» Verifying snapshot build..."
"${MVN}" clean verify -B

# ── Commit and push ───────────────────────────────────────────────
echo "» Committing version bump..."
git add -A
git commit -m "post-release: bump to ${NEXT_VERSION}"

echo "» Pushing..."
git push origin main

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  main is now at ${NEXT_VERSION}"
echo "═══════════════════════════════════════════════════════════"
