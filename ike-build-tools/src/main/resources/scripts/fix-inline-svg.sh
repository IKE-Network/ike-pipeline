#!/bin/sh
# ──────────────────────────────────────────────────────────────────────
# fix-inline-svg.sh — Fix invalid SVG elements in generated HTML files
#
# Usage:  fix-inline-svg.sh <html-file>
#
# Removes bare <rect/> elements (no attributes) that Mermaid generates
# as non-functional placeholders inside flowchart label groups.  These
# elements violate the SVG spec (missing required width/height) and
# cause warnings in Prince and other PDF renderers.
#
# Exits 0 if the file does not exist (renderer may have been skipped).
# ──────────────────────────────────────────────────────────────────────

HTML_FILE="${1:?Usage: fix-inline-svg.sh <html-file>}"

# Silently exit if the HTML file does not exist (renderer was skipped)
if [ ! -f "$HTML_FILE" ]; then
    exit 0
fi

# Count bare rects before fixing (for summary output)
count=$(perl -ne '$c++ while /<rect\/>/g; END { print $c // 0 }' "$HTML_FILE")

if [ "$count" -gt 0 ]; then
    # Remove bare <rect/> elements — portable across macOS and Linux
    perl -pi -e 's/<rect\/>//g' "$HTML_FILE"
    echo "  fix-inline-svg: removed $count bare <rect/> elements from $(basename "$HTML_FILE")"
fi

exit 0
