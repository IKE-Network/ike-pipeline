#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────
# patch-docbook-xsl.sh — Fix Saxon warnings in stock DocBook XSL
# ──────────────────────────────────────────────────────────────────
#
# Patches the downloaded DocBook XSL 1.79.2 stylesheets in the
# staging directory to suppress two Saxon XSLT processor warnings:
#
#   SXWN9019  utility.xsl included or imported more than once
#   SXWN9001  variable with no following sibling has no effect
#
# Called by exec-maven-plugin during generate-resources, after
# download-maven-plugin unpacks the distribution.
#
# Usage: patch-docbook-xsl.sh <staging-directory>
# ──────────────────────────────────────────────────────────────────
set -euo pipefail

STAGING="${1:?Usage: patch-docbook-xsl.sh <staging-directory>}"

# 1. fo/docbook.xsl: Remove direct include of utility.xsl.
#    It is already imported via addns.xsl (line 80), creating a
#    diamond import that triggers Saxon SXWN9019.
perl -pi -e 's/<xsl:include href="\.\.\/common\/utility\.xsl"\/>//' \
    "${STAGING}/fo/docbook.xsl"

# 2. fo/math.xsl: Remove dead variable in TeX equation template.
#    The template at lines 61-67 computes $output.delims but never
#    uses it, triggering Saxon SXWN9001.
perl -0777 -pi -e 's/<xsl:variable name="output\.delims">.*?<\/xsl:variable>//s' \
    "${STAGING}/fo/math.xsl"

echo "Patched DocBook XSL in ${STAGING}"
