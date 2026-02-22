#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────
# post-release.sh — Bump to next SNAPSHOT after release
# ────────────────────────────────────────────────────────────────────
#
# Usage:
#   ./post-release.sh <next-version> [--dry-run]
#
# Prerequisites:
#   - On main branch, at a release tag
#
# What it will do:
#   1. Validate prerequisites (on main, at release tag)
#   2. Set version to next SNAPSHOT (e.g., 1.3.0-SNAPSHOT)
#   3. Commit version change
#   4. Push
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
NEXT_VERSION=""

# ── Parse arguments ───────────────────────────────────────────────
usage() {
    echo "Usage: $0 <next-version> [--dry-run]"
    echo ""
    echo "  next-version   Next development version (e.g., 1.3.0-SNAPSHOT)"
    echo "  --dry-run      Show what would happen without making changes"
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

# ── TODO: Implementation ─────────────────────────────────────────
# 1. require_branch_pattern "^main$"
# 2. Verify current commit is tagged with release/*
# 3. mvn versions:set -DnewVersion="$NEXT_VERSION"
# 4. git add -A && git commit -m "post-release: $NEXT_VERSION"
# 5. git push origin main

echo "post-release.sh is a placeholder — implementation pending."
echo "See ike-workspace-conventions.adoc for design constraints."
exit 0
