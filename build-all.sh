#!/bin/bash
# ============================================================================
# IKE Documentation Pipeline - Reactor Build
# Version: 12 — Maven reactor aggregator with property-gated PDF profiles
#
# Usage:
#   ./build-all.sh                   # HTML + PDF (default: prawn)
#   ./build-all.sh --pdf=prawn       # HTML + PDF via asciidoctorj-pdf/Prawn
#   ./build-all.sh --pdf=xep         # HTML + DocBook → XSL-FO → XEP PDF
#   ./build-all.sh --pdf=prince      # HTML → Prince XML → PDF
#   ./build-all.sh --pdf=ah          # HTML → Antenna House → PDF
#   ./build-all.sh --pdf=weasyprint  # HTML → WeasyPrint → PDF
#   ./build-all.sh --pdf=all         # Build all available renderers
#   ./build-all.sh --pdf=none        # HTML only, skip PDF
#   ./build-all.sh --quiet           # Suppress Maven output
#
# This is a Maven reactor project.  Running 'mvn clean verify' from the
# project root builds all modules in dependency order.  This script adds
# PDF renderer selection and the svgo post-processing step for Prawn.
# ============================================================================
set -euo pipefail

# ── Parse arguments ─────────────────────────────────────────────────
PDF_RENDERER="prawn"
MVN_QUIET=""
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for arg in "$@"; do
    case "$arg" in
        --pdf=*)  PDF_RENDERER="${arg#--pdf=}" ;;
        --quiet)  MVN_QUIET="-q" ;;
        --help|-h)
            echo "Usage: ./build-all.sh [--pdf=RENDERER] [--quiet]"
            echo ""
            echo "PDF renderers:"
            echo "  prawn       asciidoctorj-pdf/Prawn (default, no license)"
            echo "  fop         Apache FOP via DocBook XSL-FO (free, pure Java)"
            echo "  xep         RenderX XEP via DocBook XSL-FO (free personal license)"
            echo "  prince      Prince XML (commercial, \$495+ per seat)"
            echo "  ah          Antenna House Formatter (commercial)"
            echo "  weasyprint  WeasyPrint (open source, pip install)"
            echo "  all         Build all available renderers"
            echo "  none        HTML only, skip PDF"
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg"
            echo "Try: ./build-all.sh --help"
            exit 1
            ;;
    esac
done

echo "=== IKE Build-All v12 (PDF renderer: ${PDF_RENDERER}) ==="

# ── Quick prerequisite check ────────────────────────────────────────
MISSING=()
command -v mvn   &>/dev/null || MISSING+=("mvn (Maven 4+)")
command -v java  &>/dev/null || MISSING+=("java (JDK 17+)")

case "$PDF_RENDERER" in
    prawn)
        command -v node &>/dev/null || MISSING+=("node (Node.js 18+ — needed for svgo)")
        ;;
    prince)
        command -v "${PRINCE_EXECUTABLE:-prince}" &>/dev/null || \
            MISSING+=("prince (Prince XML — https://www.princexml.com/download/)")
        command -v node &>/dev/null || MISSING+=("node (Node.js 18+ — needed for svgo)")
        ;;
    ah)
        command -v "${AH_EXECUTABLE:-AHFCmd}" &>/dev/null || \
            MISSING+=("AHFCmd (Antenna House Formatter — https://www.antennahouse.com/trial-formatter)")
        command -v node &>/dev/null || MISSING+=("node (Node.js 18+ — needed for svgo)")
        ;;
    weasyprint)
        command -v "${WEASYPRINT_EXECUTABLE:-weasyprint}" &>/dev/null || \
            MISSING+=("weasyprint (pip install weasyprint)")
        ;;
    fop)
        command -v node &>/dev/null || MISSING+=("node (Node.js 18+ — needed for svgo)")
        ;;
    xep)
        command -v "${XEP_EXECUTABLE:-xep}" &>/dev/null || \
            MISSING+=("xep (RenderX XEP — https://www.renderx.com/download/personal.html)")
        command -v node &>/dev/null || MISSING+=("node (Node.js 18+ — needed for svgo)")
        ;;
    all)
        command -v node &>/dev/null || MISSING+=("node (Node.js 18+ — needed for svgo)")
        ;;
    none) ;;
    *)
        echo "ERROR: Unknown PDF renderer: ${PDF_RENDERER}"
        echo "  Valid options: prawn, fop, prince, ah, weasyprint, xep, all, none"
        exit 1
        ;;
esac

if [[ ${#MISSING[@]} -gt 0 ]]; then
    echo "ERROR: Missing prerequisites:"
    for m in "${MISSING[@]}"; do
        echo "  - $m"
    done
    echo ""
    echo "Run ./ike-setup.sh to install all prerequisites."
    exit 1
fi

# ── Guard against poisoned MAVEN_OPTS ───────────────────────────────
if [[ -n "${MAVEN_OPTS:-}" ]]; then
    if echo "$MAVEN_OPTS" | grep -q " "; then
        echo "WARNING: MAVEN_OPTS contains spaces, which can break Java."
        echo "  Current: MAVEN_OPTS=${MAVEN_OPTS}"
        echo "  Clearing MAVEN_OPTS for this build."
        unset MAVEN_OPTS
    fi
fi

echo "  mvn:  $(command -v mvn)"
echo "  java: $(command -v java)"
if [[ "$PDF_RENDERER" == "prawn" || "$PDF_RENDERER" == "fop" || "$PDF_RENDERER" == "xep" || "$PDF_RENDERER" == "prince" || "$PDF_RENDERER" == "ah" || "$PDF_RENDERER" == "all" ]]; then
    echo "  node: $(command -v node)"
    if ! command -v svgo &>/dev/null; then
        echo ""
        echo "── Installing svgo (one-time) ──"
        npm install -g svgo 2>/dev/null || npm install -g svgo
    fi
    echo "  svgo: $(command -v svgo) ($(svgo --version 2>/dev/null || echo 'unknown'))"
fi
echo "================================================"

# ── Kroki server URL (override with KROKI_SERVER_URL env var) ───────
KROKI_OPTS=""
if [[ -n "${KROKI_SERVER_URL:-}" ]]; then
    KROKI_OPTS="-Dkroki.server.url=${KROKI_SERVER_URL}"
    echo ""
    echo "── Using custom Kroki server: ${KROKI_SERVER_URL} ──"
fi

# ── Build infrastructure modules via reactor ────────────────────────
#
# The root pom.xml is a pure aggregator that lists modules in
# dependency order.  We install everything except example-project
# first, then build example-project with the selected renderer.
#
echo ""
echo "── Installing infrastructure modules (reactor) ──"
mvn clean install -pl '!example-project,!doc-example' $MVN_QUIET

# ── Build example-project with selected PDF renderer ────────────────
#
# Profile activation is property-gated (Maven 4 consumer POM defense):
#
#   Profile          Activation property
#   ─────────────    ──────────────────────
#   html             activeByDefault (always on unless -P overrides)
#   pdf (Prawn)      -Dike.pdf.prawn
#   pdf-fop          -Dike.pdf.fop
#   pdf-xep          -Dike.pdf.xep
#   pdf-prince       -Dike.pdf.prince
#   pdf-ah           -Dike.pdf.ah
#   pdf-weasyprint   -Dike.pdf.weasyprint
#

case "$PDF_RENDERER" in
    prawn)
        echo ""
        echo "── Step 1/3: Building HTML + PDF (Prawn) ──"
        mvn clean verify -pl doc-example,example-project -Dike.pdf.prawn -Dike.pdf.default=${PDF_RENDERER} $MVN_QUIET $KROKI_OPTS

        # Step 2: Fix SVGs for prawn-svg
        #
        # prawn-svg cannot parse <style> blocks with CSS class selectors.
        # svgo inlines those CSS rules as element-level style attributes.
        SVG_COUNT=0

        for SVG_DIR in "doc-example/target/generated-docs/pdf-prawn" "doc-example/.asciidoctor/diagram" \
                       "example-project/target/generated-docs/pdf-prawn" "example-project/.asciidoctor/diagram"; do
            if [[ -d "$SVG_DIR" ]]; then
                while IFS= read -r -d '' svg; do
                    svgo --config="$SCRIPT_DIR/svgo.config.mjs" --quiet "$svg"
                    echo "    ✓ $(basename "$svg")"
                    ((SVG_COUNT++))
                done < <(find "$SVG_DIR" -name "*.svg" -type f -print0 2>/dev/null)
            fi
        done

        if [[ $SVG_COUNT -gt 0 ]]; then
            echo ""
            echo "── Step 2/3: Fixed $SVG_COUNT SVGs (CSS inlined for prawn-svg) ──"
            echo ""
            echo "── Step 3/3: Rebuilding PDF with fixed SVGs ──"
            mvn verify -pl doc-example,example-project -Dike.pdf.prawn -Dike.pdf.default=${PDF_RENDERER} $MVN_QUIET $KROKI_OPTS
        else
            echo ""
            echo "── Step 2/3: No SVGs found — skipping fix ──"
            echo "── Step 3/3: Skipped (no SVGs to fix) ──"
        fi
        ;;

    prince|ah|weasyprint)
        case "$PDF_RENDERER" in
            prince)     PDF_PROP="ike.pdf.prince" ;;
            ah)         PDF_PROP="ike.pdf.ah" ;;
            weasyprint) PDF_PROP="ike.pdf.weasyprint" ;;
        esac

        echo ""
        echo "── Building HTML + PDF (${PDF_RENDERER}) ──"
        mvn clean verify -pl doc-example,example-project -D${PDF_PROP} -Dike.pdf.default=${PDF_RENDERER} $MVN_QUIET $KROKI_OPTS
        ;;

    fop)
        echo ""
        echo "── Building HTML + PDF (FOP: DocBook → XSL-FO → PDF) ──"
        mvn clean verify -pl doc-example,example-project -Dike.pdf.fop -Dike.pdf.default=${PDF_RENDERER} $MVN_QUIET $KROKI_OPTS
        ;;

    xep)
        echo ""
        echo "── Building HTML + PDF (XEP: DocBook → XSL-FO → PDF) ──"
        mvn clean verify -pl doc-example,example-project -Dike.pdf.xep -Dike.pdf.default=${PDF_RENDERER} $MVN_QUIET $KROKI_OPTS
        ;;

    all)
        echo ""
        echo "── Building all available PDF renderers ──"
        PDF_FLAGS="-Dike.pdf.prawn -Dike.pdf.fop"
        RENDERERS=("prawn" "fop")

        command -v "${XEP_EXECUTABLE:-xep}" &>/dev/null && {
            PDF_FLAGS="$PDF_FLAGS -Dike.pdf.xep"; RENDERERS+=("xep"); }
        command -v "${PRINCE_EXECUTABLE:-prince}" &>/dev/null && {
            PDF_FLAGS="$PDF_FLAGS -Dike.pdf.prince"; RENDERERS+=("prince"); }
        command -v "${WEASYPRINT_EXECUTABLE:-weasyprint}" &>/dev/null && {
            PDF_FLAGS="$PDF_FLAGS -Dike.pdf.weasyprint"; RENDERERS+=("weasyprint"); }
        command -v "${AH_EXECUTABLE:-AHFCmd}" &>/dev/null && {
            PDF_FLAGS="$PDF_FLAGS -Dike.pdf.ah"; RENDERERS+=("ah"); }

        echo "  Available: ${RENDERERS[*]}"

        # Single Maven build — all profiles coexist
        mvn clean verify -pl doc-example,example-project $PDF_FLAGS $MVN_QUIET $KROKI_OPTS

        # Post-build: fix Prawn SVGs (prawn-svg can't handle CSS class selectors)
        SVG_COUNT=0
        for SVG_DIR in "doc-example/target/generated-docs/pdf-prawn" \
                       "doc-example/.asciidoctor/diagram" \
                       "example-project/target/generated-docs/pdf-prawn" \
                       "example-project/.asciidoctor/diagram"; do
            if [[ -d "$SVG_DIR" ]]; then
                while IFS= read -r -d '' svg; do
                    svgo --config="$SCRIPT_DIR/svgo.config.mjs" --quiet "$svg"
                    ((SVG_COUNT++))
                done < <(find "$SVG_DIR" -name "*.svg" -type f -print0 2>/dev/null)
            fi
        done
        if [[ $SVG_COUNT -gt 0 ]]; then
            echo ""
            echo "── Fixed $SVG_COUNT SVGs — rebuilding Prawn PDF ──"
            mvn verify -pl doc-example,example-project -Dike.pdf.prawn $MVN_QUIET $KROKI_OPTS
        fi

        echo ""
        echo "=== All Renderers Complete ==="
        for r in "${RENDERERS[@]}"; do
            echo "  PDF ($r): example-project/target/generated-docs/pdf-$r/index.pdf"
        done
        exit 0
        ;;

    none)
        echo ""
        echo "── Building HTML only (PDF skipped) ──"
        mvn clean verify -pl doc-example,example-project $MVN_QUIET $KROKI_OPTS
        ;;
esac

# ── Results ─────────────────────────────────────────────────────────
echo ""
echo "=== Build Complete ==="
echo "  HTML:    example-project/target/generated-docs/html/index.html"
if [[ "$PDF_RENDERER" != "none" ]]; then
    echo "  PDF:     example-project/target/generated-docs/pdf-${PDF_RENDERER}/index.pdf  (classifier: ${PDF_RENDERER})"
    echo "  Default: example-project/target/generated-docs/pdf/index.pdf  (classifier: pdf)"
fi

if [[ "$(uname -s)" == "Darwin" ]]; then
    echo ""
    echo "To view: open example-project/target/generated-docs/html/index.html"
fi
