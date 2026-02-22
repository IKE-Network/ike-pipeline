#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────
# merge-to-main.sh — Merge a feature branch to main
# ────────────────────────────────────────────────────────────────────
#
# Usage:
#   ./merge-to-main.sh [--dry-run]
#
# Prerequisites:
#   - On a feature branch (not main)
#   - Working tree clean
#   - All tests passing
#
# What it will do:
#   1. Validate prerequisites
#   2. Strip branch qualifier from version
#      (e.g., 1.2.0-shield-terminology-SNAPSHOT → 1.2.0-SNAPSHOT)
#   3. Commit version change
#   4. Merge to main (or create PR-ready branch)
#   5. Tag the merge point
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

# ── Parse arguments ───────────────────────────────────────────────
usage() {
    echo "Usage: $0 [--dry-run]"
    echo ""
    echo "  --dry-run   Show what would happen without making changes"
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run) DRY_RUN=true; shift ;;
        --help|-h) usage ;;
        -*) echo "Unknown option: $1"; usage ;;
        *) echo "Unexpected argument: $1"; usage ;;
    esac
done

# ── TODO: Implementation ─────────────────────────────────────────
# 1. require_clean_worktree
# 2. Verify not on main: require_branch_pattern "^(?!main$)"
# 3. Extract base version from current version
#    CURRENT=$(mvn_version)
#    BASE=$(echo "$CURRENT" | sed 's/-SNAPSHOT//' | sed 's/-[a-z].*$//')
#    MAIN_VERSION="${BASE}-SNAPSHOT"
# 4. mvn versions:set -DnewVersion="$MAIN_VERSION"
# 5. git add -A && git commit -m "merge: strip branch qualifier → $MAIN_VERSION"
# 6. git checkout main && git merge --no-ff "$BRANCH"
# 7. git tag "merge/$BRANCH"
# 8. git push origin main --tags

echo "merge-to-main.sh is a placeholder — implementation pending."
echo "See ike-workspace-conventions.adoc for design constraints."
exit 0
