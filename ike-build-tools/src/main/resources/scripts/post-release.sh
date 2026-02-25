#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────
# post-release.sh — Bump main to next SNAPSHOT after release
# ────────────────────────────────────────────────────────────────────
#
# Usage:
#   ./post-release.sh <next-version> [--dry-run]
#
# Example:
#   ./post-release.sh 1.2.0-SNAPSHOT
#
# Prerequisites:
#   - CI has merged the release branch back to main
#   - On main branch
#   - Working tree clean
#
# What it does:
#   1. Pulls latest main (to pick up CI merge)
#   2. Sets all POM versions to <next-version>
#   3. Commits and pushes
#
# ────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Defaults ──────────────────────────────────────────────────────
DRY_RUN=false
NEXT_VERSION=""

# ── Parse arguments ───────────────────────────────────────────────
usage() {
    cat <<EOF
Usage: $0 <next-version> [--dry-run]

  next-version   Next development version (e.g., 1.2.0-SNAPSHOT)
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

# ── Summary ───────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  Next version:  ${NEXT_VERSION}"
echo "  Branch:        ${CURRENT_BRANCH}"
echo "  Dry run:       ${DRY_RUN}"
echo "═══════════════════════════════════════════════════════════"
echo ""

if [[ "${DRY_RUN}" == "true" ]]; then
    echo "[DRY RUN] Would pull latest main"
    echo "[DRY RUN] Would set version to: ${NEXT_VERSION}"
    echo "[DRY RUN] Would commit and push"
    exit 0
fi

# ── Pull latest (CI merge) ────────────────────────────────────────
echo "» Pulling latest main..."
git pull origin main

# ── Set versions ──────────────────────────────────────────────────
echo "» Setting POM versions to: ${NEXT_VERSION}"
mvn versions:set -DnewVersion="${NEXT_VERSION}" -DgenerateBackupPoms=false -q

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
