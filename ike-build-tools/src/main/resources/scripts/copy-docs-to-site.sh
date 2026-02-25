#!/bin/sh
# ──────────────────────────────────────────────────────────────────────
# copy-docs-to-site.sh — Copy rendered HTML docs into Maven site output
#
# Usage:  copy-docs-to-site.sh <generated-docs-dir> <site-dir> <document-name>
#
#   generated-docs-dir  Path to target/generated-docs/html
#   site-dir            Path to target/site
#   document-name       Name for the docs subdirectory (e.g. project artifactId)
#
# Copies the rendered HTML document and its assets (SVG diagrams, images)
# into target/site/docs/ so they are deployed alongside the Maven site.
#
# Exits 0 if the generated-docs directory does not exist (HTML generation
# may have been skipped).
# ──────────────────────────────────────────────────────────────────────

GENERATED_DOCS="${1:?Usage: copy-docs-to-site.sh <generated-docs-dir> <site-dir> <document-name>}"
SITE_DIR="${2:?Usage: copy-docs-to-site.sh <generated-docs-dir> <site-dir> <document-name>}"
DOC_NAME="${3:?Usage: copy-docs-to-site.sh <generated-docs-dir> <site-dir> <document-name>}"

# Silently exit if no HTML was generated (renderer was skipped)
if [ ! -d "$GENERATED_DOCS" ]; then
    exit 0
fi

# Count HTML files — exit if none
html_count=$(find "$GENERATED_DOCS" -maxdepth 1 -name '*.html' | wc -l | tr -d ' ')
if [ "$html_count" -eq 0 ]; then
    exit 0
fi

DEST="${SITE_DIR}/docs"
mkdir -p "$DEST"

# Copy HTML files and supporting assets (SVG diagrams, images)
cp "$GENERATED_DOCS"/*.html "$DEST/" 2>/dev/null
cp "$GENERATED_DOCS"/*.svg  "$DEST/" 2>/dev/null
cp "$GENERATED_DOCS"/*.png  "$DEST/" 2>/dev/null
cp "$GENERATED_DOCS"/*.jpg  "$DEST/" 2>/dev/null
cp "$GENERATED_DOCS"/*.css  "$DEST/" 2>/dev/null

echo "  copy-docs-to-site: copied $html_count HTML file(s) to $(basename "$SITE_DIR")/docs/"

exit 0
