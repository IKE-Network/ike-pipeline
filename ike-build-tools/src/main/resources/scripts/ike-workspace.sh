#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────
# ike-workspace.sh — Create, list, resume, or remove IKE Workspaces
# ────────────────────────────────────────────────────────────────────
#
# An IKE Workspace is a full Git clone in a conventionally named
# directory under $IKE_WORKSPACE_HOME (default ~/Projects/ike).
# Each active branch gets its own workspace with a branch-qualified
# Maven version for artifact isolation.
#
# Usage:
#   ike-workspace <branch-name>              # create or resume
#   ike-workspace <branch-name> --from-here  # branch from current workspace
#   ike-workspace --init <remote-url>        # first-time setup (clone main)
#   ike-workspace --list                     # show all workspaces
#   ike-workspace --remove <branch-name>     # remove with safety checks
#   ike-workspace --help                     # this help message
#
# Environment:
#   IKE_WORKSPACE_HOME  — workspace parent directory (default ~/Projects/ike)
#
# See ike-workspace-conventions.adoc for the full rationale.
#
# ────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions if available
if [[ -f "${SCRIPT_DIR}/common-functions.sh" ]]; then
    # shellcheck source=common-functions.sh
    source "${SCRIPT_DIR}/common-functions.sh"
else
    # Minimal fallback if common-functions.sh is not available
    log_info()  { echo "[INFO] $*"; }
    log_warn()  { echo "[WARN] $*" >&2; }
    log_error() { echo "[ERROR] $*" >&2; }
fi

# ── Configuration ──────────────────────────────────────────────────
IKE_HOME="${IKE_WORKSPACE_HOME:-${HOME}/Projects/ike}"

# ── Helpers ────────────────────────────────────────────────────────

# Transform branch name to safe directory name (/ → -)
safe_name() {
    echo "$1" | tr '/' '-'
}

# Discover the origin remote URL from any existing workspace
discover_origin_url() {
    local git_dir
    git_dir=$(find "${IKE_HOME}" -maxdepth 2 -name .git -type d -print -quit 2>/dev/null)
    if [[ -z "${git_dir}" ]]; then
        return 1
    fi
    git -C "${git_dir}/.." remote get-url origin 2>/dev/null
}

usage() {
    echo "Usage: ike-workspace <branch-name> [--from-here]"
    echo "       ike-workspace --init <remote-url>"
    echo "       ike-workspace --list"
    echo "       ike-workspace --remove <branch-name>"
    echo ""
    echo "  <branch-name>      Git branch (e.g., shield/terminology-refresh)"
    echo "  --from-here        Branch from current workspace's branch"
    echo "  --init <url>       First-time setup: clone main workspace"
    echo "  --list             Show all workspaces with status"
    echo "  --remove <branch>  Remove a workspace (with safety checks)"
    echo ""
    echo "Environment:"
    echo "  IKE_WORKSPACE_HOME  Parent directory (default ~/Projects/ike)"
    exit 1
}

# ── Parse arguments ───────────────────────────────────────────────
ACTION=""
BRANCH=""
FROM_HERE=false
INIT_URL=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --init)
            ACTION="init"
            if [[ $# -lt 2 ]]; then
                log_error "--init requires a remote URL argument."
                usage
            fi
            INIT_URL="$2"
            shift 2
            ;;
        --list)
            ACTION="list"
            shift
            ;;
        --remove)
            ACTION="remove"
            if [[ $# -lt 2 ]]; then
                log_error "--remove requires a branch name argument."
                usage
            fi
            BRANCH="$2"
            shift 2
            ;;
        --from-here)
            FROM_HERE=true
            shift
            ;;
        --help|-h)
            usage
            ;;
        -*)
            log_error "Unknown option: $1"
            usage
            ;;
        *)
            BRANCH="$1"
            shift
            ;;
    esac
done

# Default action is create/resume if a branch was given
if [[ -z "${ACTION}" && -n "${BRANCH}" ]]; then
    ACTION="create"
fi

if [[ -z "${ACTION}" ]]; then
    log_error "No action specified."
    usage
fi

# ── Action: init ──────────────────────────────────────────────────
do_init() {
    local url="$1"
    local workspace="${IKE_HOME}/main"

    if [[ -d "${workspace}/.git" ]]; then
        log_info "Main workspace already exists at ${workspace}"
        return 0
    fi

    log_info "Initializing IKE Workspace home: ${IKE_HOME}"
    mkdir -p "${IKE_HOME}"

    echo "═══════════════════════════════════════════════════════════"
    echo "  IKE Workspace — First-Time Setup"
    echo "  Remote: ${url}"
    echo "  Target: ${workspace}"
    echo "═══════════════════════════════════════════════════════════"

    git clone "${url}" "${workspace}"
    log_info "Main workspace created at ${workspace}"
}

# ── Action: list ──────────────────────────────────────────────────
do_list() {
    if [[ ! -d "${IKE_HOME}" ]]; then
        log_warn "No workspace home found at ${IKE_HOME}"
        log_warn "Run: ike-workspace --init <remote-url>"
        return 0
    fi

    echo "IKE Workspaces in ${IKE_HOME}:"
    echo ""

    local found=false
    for dir in "${IKE_HOME}"/*/; do
        [[ -d "${dir}/.git" ]] || continue
        found=true

        local name
        name="$(basename "${dir}")"
        local branch
        branch="$(git -C "${dir}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "???")"

        # Count uncommitted changes
        local dirty_count
        dirty_count="$(git -C "${dir}" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"

        local status_suffix=""
        if [[ "${dirty_count}" -gt 0 ]]; then
            status_suffix=" (${dirty_count} uncommitted)"
        fi

        printf "  %-30s  →  %s%s\\n" "${name}" "${branch}" "${status_suffix}"
    done

    if [[ "${found}" == "false" ]]; then
        echo "  (no workspaces found)"
    fi
}

# ── Action: remove ────────────────────────────────────────────────
do_remove() {
    local branch="$1"
    local safe
    safe="$(safe_name "${branch}")"
    local workspace="${IKE_HOME}/${safe}"

    if [[ ! -d "${workspace}" ]]; then
        log_error "Workspace not found: ${workspace}"
        exit 1
    fi

    # Safety check: uncommitted changes
    local dirty_count
    dirty_count="$(git -C "${workspace}" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
    if [[ "${dirty_count}" -gt 0 ]]; then
        log_warn "Workspace has ${dirty_count} uncommitted change(s)."
        read -r -p "Remove anyway? [y/N] " confirm
        if [[ "${confirm}" != "y" && "${confirm}" != "Y" ]]; then
            log_info "Aborted."
            exit 0
        fi
    fi

    # Safety check: unpushed commits
    local ahead
    ahead="$(git -C "${workspace}" rev-list @{u}..HEAD --count 2>/dev/null)" || ahead="0"
    if [[ "${ahead}" -gt 0 ]]; then
        log_warn "Workspace has ${ahead} unpushed commit(s)."
        read -r -p "Remove anyway? [y/N] " confirm
        if [[ "${confirm}" != "y" && "${confirm}" != "Y" ]]; then
            log_info "Aborted."
            exit 0
        fi
    fi

    echo "═══════════════════════════════════════════════════════════"
    echo "  Removing workspace: ${safe}"
    echo "  Directory: ${workspace}"
    echo "═══════════════════════════════════════════════════════════"

    rm -rf "${workspace}"
    log_info "Workspace removed: ${safe}"
}

# ── Action: create/resume ─────────────────────────────────────────
do_create() {
    local branch="$1"
    local safe
    safe="$(safe_name "${branch}")"
    local workspace="${IKE_HOME}/${safe}"

    # Resume if workspace already exists
    if [[ -d "${workspace}/.git" ]]; then
        echo "═══════════════════════════════════════════════════════════"
        echo "  Resuming workspace: ${safe}"
        echo "  Branch: ${branch}"
        echo "  Directory: ${workspace}"
        echo "═══════════════════════════════════════════════════════════"

        git -C "${workspace}" fetch origin
        log_info "Workspace resumed. Run: cd ${workspace}"
        return 0
    fi

    # Discover origin URL
    local origin_url
    if [[ "${FROM_HERE}" == "true" ]]; then
        # Must be inside an existing workspace
        if ! git rev-parse --is-inside-work-tree &>/dev/null; then
            log_error "--from-here must be run from inside an existing workspace."
            exit 1
        fi

        origin_url="$(git remote get-url origin)"
        local current_branch
        current_branch="$(git rev-parse --abbrev-ref HEAD)"

        # Push current branch to remote first
        log_info "Pushing current branch '${current_branch}' to remote..."
        git push -u origin "${current_branch}"
    else
        origin_url="$(discover_origin_url)" || {
            log_error "No existing workspace found to discover remote URL."
            log_error "Run first: ike-workspace --init <remote-url>"
            exit 1
        }
    fi

    mkdir -p "${IKE_HOME}"

    echo "═══════════════════════════════════════════════════════════"
    echo "  Creating workspace: ${safe}"
    echo "  Branch: ${branch}"
    echo "  Remote: ${origin_url}"
    echo "  Directory: ${workspace}"
    if [[ "${FROM_HERE}" == "true" ]]; then
        local current_branch
        current_branch="$(git rev-parse --abbrev-ref HEAD)"
        echo "  From: ${current_branch}"
    fi
    echo "═══════════════════════════════════════════════════════════"

    # Clone
    git clone "${origin_url}" "${workspace}"

    # Checkout or create the branch
    if [[ "${FROM_HERE}" == "true" ]]; then
        local source_branch
        source_branch="$(git rev-parse --abbrev-ref HEAD)"
        git -C "${workspace}" checkout -b "${branch}" "origin/${source_branch}"
    else
        # Try to checkout existing remote branch, or create from origin/main
        if git -C "${workspace}" ls-remote --exit-code --heads origin "${branch}" &>/dev/null; then
            git -C "${workspace}" checkout -b "${branch}" "origin/${branch}"
        else
            git -C "${workspace}" checkout -b "${branch}" origin/main
        fi
    fi

    # Set branch-qualified version (skip for main branch)
    if [[ "${branch}" != "main" ]]; then
        log_info "Setting branch-qualified version..."
        local current_version
        current_version="$(cd "${workspace}" && mvn help:evaluate -Dexpression=project.version -q -DforceStdout)"

        # Extract base version: strip -SNAPSHOT and any existing branch qualifier
        local base_version
        base_version="$(echo "${current_version}" | sed 's/-SNAPSHOT//' | sed 's/-[a-z].*$//')"

        local new_version="${base_version}-${safe}-SNAPSHOT"

        (cd "${workspace}" && mvn versions:set -DnewVersion="${new_version}" -DgenerateBackupPoms=false -q)
        git -C "${workspace}" add -A
        git -C "${workspace}" commit -m "workspace: version ${new_version} for branch ${branch}"

        log_info "Version set to: ${new_version}"
    fi

    log_info "Workspace created. Run: cd ${workspace}"
}

# ── Dispatch ──────────────────────────────────────────────────────
case "${ACTION}" in
    init)   do_init "${INIT_URL}" ;;
    list)   do_list ;;
    remove) do_remove "${BRANCH}" ;;
    create) do_create "${BRANCH}" ;;
    *)      log_error "Unknown action: ${ACTION}"; usage ;;
esac
