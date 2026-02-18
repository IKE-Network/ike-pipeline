#!/bin/sh
# ──────────────────────────────────────────────────────────────────────
# scan-renderer-logs.sh — Scan renderer log files for errors
#
# Usage:  scan-renderer-logs.sh <logs-directory>
#
# Finds all renderer-*.log files in the given directory, greps each
# for error patterns, and prints a one-line summary per file.
# Exits 0 regardless — renderer exit codes already fail the build.
# ──────────────────────────────────────────────────────────────────────

LOGS_DIR="${1:?Usage: scan-renderer-logs.sh <logs-directory>}"

# Silently exit if the logs directory does not exist (no renderers ran)
if [ ! -d "$LOGS_DIR" ]; then
    exit 0
fi

# Collect log files
LOG_FILES=$(find "$LOGS_DIR" -maxdepth 1 -name 'renderer-*.log' -type f 2>/dev/null | sort)

# Nothing to scan
if [ -z "$LOG_FILES" ]; then
    exit 0
fi

echo ""
echo "── Renderer Log Summary ──────────────────────────────────────"

for log_file in $LOG_FILES; do
    name=$(basename "$log_file" .log | sed 's/^renderer-//')
    lines=$(wc -l < "$log_file" | tr -d ' ')
    errors=$(grep -c -i -E 'error|fatal|exception|failed|not found' "$log_file" 2>/dev/null || echo 0)

    if [ "$errors" -gt 0 ]; then
        echo "  [$name] $errors error(s) — see $log_file"
    else
        echo "  [$name] OK ($lines lines)"
    fi
done

echo ""
exit 0
