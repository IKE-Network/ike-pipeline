#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────
# common-functions.sh — Shared utility functions for IKE build scripts
# ────────────────────────────────────────────────────────────────────
#
# Source this file from other scripts:
#
#   SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#   source "${SCRIPT_DIR}/common-functions.sh"
#
# Provides:
#   log_info, log_warn, log_error  — colored output helpers
#   require_clean_worktree         — verify git working tree is clean
#   require_branch_pattern         — validate current branch matches regex
#   mvn_version                    — extract current Maven project version
#   current_branch                 — echo current git branch name
#   is_ahead_of_remote             — return 0 if unpushed commits exist
#
# ────────────────────────────────────────────────────────────────────

# ── Color setup ────────────────────────────────────────────────────
if [[ -t 1 ]] && command -v tput &>/dev/null; then
    RED=$(tput setaf 1)
    GREEN=$(tput setaf 2)
    YELLOW=$(tput setaf 3)
    RESET=$(tput sgr0)
else
    RED=""
    GREEN=""
    YELLOW=""
    RESET=""
fi

# ── Logging ────────────────────────────────────────────────────────

log_info() {
    echo "${GREEN}[INFO]${RESET} $*"
}

log_warn() {
    echo "${YELLOW}[WARN]${RESET} $*" >&2
}

log_error() {
    echo "${RED}[ERROR]${RESET} $*" >&2
}

# ── Git helpers ────────────────────────────────────────────────────

# Exits with error if the git working tree has uncommitted changes.
require_clean_worktree() {
    if ! git diff --quiet || ! git diff --cached --quiet; then
        log_error "Working tree is not clean."
        log_error "Commit or stash changes before proceeding."
        exit 1
    fi
}

# Exits with error if the current branch does not match the given regex.
# Usage: require_branch_pattern "^(feature|shield|stamp)/"
require_branch_pattern() {
    local pattern="$1"
    local branch
    branch="$(git rev-parse --abbrev-ref HEAD)"
    if ! [[ "${branch}" =~ ${pattern} ]]; then
        log_error "Current branch '${branch}' does not match required pattern: ${pattern}"
        exit 1
    fi
}

# Prints the current Maven project version.
# Optional argument: module path for -pl flag.
# Usage: mvn_version          → reactor root version
#        mvn_version ike-parent → ike-parent module version
mvn_version() {
    local module_arg=""
    if [[ $# -gt 0 ]]; then
        module_arg="-pl $1"
    fi
    # shellcheck disable=SC2086
    mvn help:evaluate ${module_arg} -Dexpression=project.version -q -DforceStdout
}

# Prints the current git branch name.
current_branch() {
    git rev-parse --abbrev-ref HEAD
}

# Returns 0 if the local branch has commits not on the remote, 1 otherwise.
# Returns 1 if there is no upstream configured.
is_ahead_of_remote() {
    local ahead
    ahead="$(git rev-list @{u}..HEAD --count 2>/dev/null)" || return 1
    [[ "${ahead}" -gt 0 ]]
}
