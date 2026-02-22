#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────
# create-checkpoint.sh — Create an immutable checkpoint from current branch
# ────────────────────────────────────────────────────────────────────
#
# Usage:
#   ./create-checkpoint.sh [--dry-run]
#
# Prerequisites:
#   - Working tree clean
#
# What it will do:
#   1. Determine checkpoint version:
#      <current-base>-checkpoint.<date>.<sequence>
#   2. Build with checkpoint version (mvn versions:set + mvn verify)
#   3. Tag in git: checkpoint/<version>
#   4. Deploy to checkpoint repository (when configured)
#   5. Restore working version (SNAPSHOT)
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
# 2. CURRENT=$(mvn_version)
# 3. BASE=$(echo "$CURRENT" | sed 's/-SNAPSHOT//')
# 4. DATE=$(date +%Y%m%d)
# 5. Determine sequence number (count existing checkpoint tags for today)
# 6. CHECKPOINT_VERSION="${BASE}-checkpoint.${DATE}.${SEQ}"
# 7. mvn versions:set -DnewVersion="$CHECKPOINT_VERSION"
# 8. mvn clean verify
# 9. git tag "checkpoint/$CHECKPOINT_VERSION"
# 10. mvn versions:set -DnewVersion="$CURRENT"  (restore SNAPSHOT)
# 11. git push origin "checkpoint/$CHECKPOINT_VERSION"

echo "create-checkpoint.sh is a placeholder — implementation pending."
echo "See ike-workspace-conventions.adoc for design constraints."
exit 0
