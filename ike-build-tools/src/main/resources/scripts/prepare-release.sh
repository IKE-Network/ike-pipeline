#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────
# prepare-release.sh — Transition from SNAPSHOT to release version
# ────────────────────────────────────────────────────────────────────
#
# Usage:
#   ./prepare-release.sh <release-version> [--dry-run]
#
# Prerequisites:
#   - On main branch
#   - Working tree clean
#   - All tests passing
#
# What it will do:
#   1. Validate prerequisites (on main, clean worktree)
#   2. Set version to release version (strip -SNAPSHOT)
#   3. Build and verify (mvn clean verify)
#   4. Commit version change
#   5. Tag: release/<version>
#   6. Deploy to release repository
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

# ── TODO: Implementation ─────────────────────────────────────────
# 1. require_clean_worktree
# 2. require_branch_pattern "^main$"
# 3. mvn versions:set -DnewVersion="$RELEASE_VERSION"
# 4. mvn clean verify
# 5. git add -A && git commit -m "release: $RELEASE_VERSION"
# 6. git tag "release/$RELEASE_VERSION"
# 7. mvn deploy
# 8. git push origin main --tags

echo "prepare-release.sh is a placeholder — implementation pending."
echo "See ike-workspace-conventions.adoc for design constraints."
exit 0
