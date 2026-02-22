#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────
# release-from-feature.sh — Create a release branch from a feature branch
# ────────────────────────────────────────────────────────────────────
# NOTE: This script predates the IKE Workspace conventions and unified
# versioning. It uses mvn versions:set which works correctly with unified
# versioning. A full rework to integrate with the workspace script
# inventory is deferred to iterative development.
# See ike-workspace-conventions.adoc for context.
# ────────────────────────────────────────────────────────────────────
#
# Usage:
#   ./release-from-feature.sh <release-version> [--dry-run]
#
# Prerequisites:
#   - On a feature branch (not main)
#   - Working tree clean
#   - All tests passing
#
# What it does:
#   1. Validates prerequisites
#   2. Creates release/<version> branch from current feature branch
#   3. Updates POM versions to <release-version>
#   4. Commits version change
#   5. Pushes release branch (triggers CI release workflow)
#
# ────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions if available
if [[ -f "${SCRIPT_DIR}/common-functions.sh" ]]; then
    # shellcheck source=common-functions.sh
    source "${SCRIPT_DIR}/common-functions.sh"
fi

# ── Defaults ──────────────────────────────────────────────────────
DRY_RUN=false
RELEASE_VERSION=""

# ── Parse arguments ───────────────────────────────────────────────
usage() {
    echo "Usage: $0 <release-version> [--dry-run]"
    echo ""
    echo "  release-version   Version to release (e.g., 1.2.0)"
    echo "  --dry-run         Show what would happen without making changes"
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run) DRY_RUN=true; shift ;;
        --help|-h) usage ;;
        -*) echo "Unknown option: $1"; usage ;;
        *) RELEASE_VERSION="$1"; shift ;;
    esac
done

if [[ -z "${RELEASE_VERSION}" ]]; then
    echo "ERROR: Release version is required."
    usage
fi

# ── Validate prerequisites ────────────────────────────────────────
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"

if [[ "${CURRENT_BRANCH}" == "main" ]]; then
    echo "ERROR: Cannot release from main branch."
    echo "       Switch to a feature branch first."
    exit 1
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "ERROR: Working tree is not clean."
    echo "       Commit or stash changes before releasing."
    exit 1
fi

RELEASE_BRANCH="release/${RELEASE_VERSION}"

echo "═══════════════════════════════════════════════════════════"
echo "  Release: ${RELEASE_VERSION}"
echo "  Source:  ${CURRENT_BRANCH}"
echo "  Branch:  ${RELEASE_BRANCH}"
echo "  Dry run: ${DRY_RUN}"
echo "═══════════════════════════════════════════════════════════"

if [[ "${DRY_RUN}" == "true" ]]; then
    echo ""
    echo "[DRY RUN] Would create branch: ${RELEASE_BRANCH}"
    echo "[DRY RUN] Would set version to: ${RELEASE_VERSION}"
    echo "[DRY RUN] Would push: ${RELEASE_BRANCH}"
    exit 0
fi

# ── Create release branch ─────────────────────────────────────────
echo ""
echo "Creating release branch: ${RELEASE_BRANCH}"
git checkout -b "${RELEASE_BRANCH}"

# ── Update POM versions ──────────────────────────────────────────
echo "Setting version to: ${RELEASE_VERSION}"
mvn versions:set -DnewVersion="${RELEASE_VERSION}" -DgenerateBackupPoms=false -q

# ── Commit and push ──────────────────────────────────────────────
echo "Committing version change..."
git add -A
git commit -m "release: set version to ${RELEASE_VERSION}"

echo "Pushing release branch..."
git push -u origin "${RELEASE_BRANCH}"

echo ""
echo "Release branch pushed. CI will handle the rest."
echo "Monitor: gh run list --branch ${RELEASE_BRANCH}"
