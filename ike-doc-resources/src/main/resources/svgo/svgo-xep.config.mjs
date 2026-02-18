// svgo-xep.config.mjs — Fix Mermaid SVGs for RenderX XEP and Apache FOP
//
// XEP's and FOP's SVG renderers do not support:
//   1. <foreignObject> — Mermaid uses these for all labels (flowchart,
//      state, ER). XEP/FOP silently drop them, so labels disappear.
//   2. HSL color values — Mermaid's theme uses hsl(h, s%, l%) in CSS.
//      XEP ignores them, causing strokes and fills to vanish.
//   3. <rect> without explicit width/height — Mermaid state diagrams
//      emit background rects without dimensions. FOP's Batik renderer
//      throws BridgeException on these (SVGRectElementBridge).
//
// This config applies eight transformations in order:
//   1. inlineStyles:          CSS rules → element-level style attributes
//   2. fixMermaidUndefined:   strip "undefined;undefined" from Mermaid ER styles
//   3. convertHslToHex:       hsl(h, s%, l%) → #rrggbb
//   4. fixMarkerOrient:       orient="auto-start-reverse" → "auto" (Batik compat)
//   5. fixAlignmentBaseline:  alignment-baseline="central" → "middle" (Batik compat)
//   6. convertRgbaToHex:      rgba(r,g,b,a) → hex + fill-opacity (Batik compat)
//   7. fixMissingRectDims:    add width="0" height="0" to bare <rect>s
//   8. replaceForeignObject:  <foreignObject> → <text>
//   9. removeStyleElement:    drop the (now-empty) <style> block
//
// PlantUML and GraphViz SVGs pass through unchanged — they already use
// native <text> elements and hex colors.

// ── HSL → Hex conversion ────────────────────────────────────────────
// Handles Mermaid's decimal-degree hue and fractional percentages.
function hslToHex(h, s, l) {
  h = ((h % 360) + 360) % 360;
  s = s / 100;
  l = l / 100;

  const c = (1 - Math.abs(2 * l - 1)) * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = l - c / 2;

  let r, g, b;
  if (h < 60)       { r = c; g = x; b = 0; }
  else if (h < 120) { r = x; g = c; b = 0; }
  else if (h < 180) { r = 0; g = c; b = x; }
  else if (h < 240) { r = 0; g = x; b = c; }
  else if (h < 300) { r = x; g = 0; b = c; }
  else              { r = c; g = 0; b = x; }

  const toHex = (v) => {
    const hex = Math.round((v + m) * 255).toString(16);
    return hex.length === 1 ? '0' + hex : hex;
  };

  return '#' + toHex(r) + toHex(g) + toHex(b);
}

// Regex matches: hsl(259.6, 59.8%, 87.9%) — with optional spaces and decimals
const HSL_RE = /hsl\(\s*([\d.]+)\s*,\s*([\d.]+)%\s*,\s*([\d.]+)%\s*\)/g;

function replaceHslValues(text) {
  return text.replace(HSL_RE, (_, h, s, l) =>
    hslToHex(parseFloat(h), parseFloat(s), parseFloat(l))
  );
}

// ── foreignObject → <text> conversion ───────────────────────────────
// Extracts text from: foreignObject > div > span > p > textContent
// Creates a centered <text> element using the foreignObject dimensions.
function extractTextFromForeignObject(foNode) {
  for (const child of foNode.children || []) {
    if (child.name === 'div') {
      for (const divChild of child.children || []) {
        if (divChild.name === 'span') {
          for (const spanChild of divChild.children || []) {
            if (spanChild.name === 'p') {
              const texts = [];
              for (const pChild of spanChild.children || []) {
                if (pChild.type === 'text') {
                  texts.push(pChild.value.trim());
                }
              }
              return texts.join('').trim();
            }
          }
          // Self-closing <span/> with no children = empty label
          return '';
        }
      }
    }
  }
  return '';
}

function getTextAnchor(foNode) {
  for (const child of foNode.children || []) {
    if (child.name === 'div') {
      const style = child.attributes?.style || '';
      if (style.includes('text-align: start')) {
        return 'start';
      }
    }
  }
  return 'middle';
}

export default {
  plugins: [
    // 1. Inline CSS rules from <style> into element attributes
    {
      name: 'inlineStyles',
      params: {
        onlyMatchedOnce: false,
        removeMatchedSelectors: true,
        useMqs: ['', 'screen'],
      },
    },

    // 2. Strip "undefined;undefined" from Mermaid ER edge style attributes
    //    Mermaid generates: style="undefined;undefined stroke:#333333;..."
    //    Note: second "undefined" is followed by a space, NOT a semicolon.
    //    This causes CSS parsers to skip the stroke declaration.
    {
      name: 'fixMermaidUndefinedStyles',
      fn: () => ({
        element: {
          enter: (node) => {
            if (node.attributes?.style) {
              // Match literal "undefined;undefined " pattern
              node.attributes.style = node.attributes.style
                .replace(/undefined;undefined\s*/g, '')
                .replace(/^undefined[;\s]*/g, '');
            }
          },
        },
      }),
    },

    // 3. Convert HSL colors to hex in both <style> text and inline styles
    {
      name: 'convertHslToHex',
      fn: () => ({
        element: {
          enter: (node) => {
            // Handle <style> element text content
            if (node.name === 'style') {
              for (const child of node.children || []) {
                if (child.type === 'text' || child.type === 'cdata') {
                  child.value = replaceHslValues(child.value);
                }
              }
            }
            // Handle inline style attributes
            if (node.attributes?.style) {
              node.attributes.style = replaceHslValues(node.attributes.style);
            }
            // Handle fill, stroke, and other color attributes
            for (const attr of ['fill', 'stroke', 'color', 'stop-color',
                                'flood-color', 'lighting-color']) {
              if (node.attributes?.[attr]) {
                node.attributes[attr] = replaceHslValues(node.attributes[attr]);
              }
            }
          },
        },
      }),
    },

    // 4. Fix <marker orient="auto-start-reverse"> for Batik/FOP
    //    Mermaid ER diagrams use orient="auto-start-reverse" (SVG2).
    //    Batik 1.19 (used by FOP) throws BridgeException on this value.
    //    Replace with "auto" which Batik supports — arrows may point
    //    the same direction but the diagram remains readable.
    {
      name: 'fixMarkerOrient',
      fn: () => ({
        element: {
          enter: (node) => {
            if (node.name !== 'marker') return;
            if (node.attributes?.orient === 'auto-start-reverse') {
              node.attributes.orient = 'auto';
            }
          },
        },
      }),
    },

    // 5. Fix alignment-baseline="central" for Batik/FOP
    //    Mermaid and the replaceForeignObject plugin below emit
    //    alignment-baseline="central" (SVG2 value). Batik 1.19 rejects
    //    this as invalid CSS, throwing DOMException and skipping the
    //    element entirely. Replace with "middle" which Batik supports.
    {
      name: 'fixAlignmentBaseline',
      fn: () => ({
        element: {
          enter: (node) => {
            // Fix attribute form: alignment-baseline="central"
            if (node.attributes?.['alignment-baseline'] === 'central') {
              node.attributes['alignment-baseline'] = 'middle';
            }
            if (node.attributes?.['dominant-baseline'] === 'central') {
              node.attributes['dominant-baseline'] = 'middle';
            }
            // Fix inline style form: alignment-baseline: central
            if (node.attributes?.style) {
              node.attributes.style = node.attributes.style
                .replace(/alignment-baseline\s*:\s*central/g, 'alignment-baseline: middle')
                .replace(/dominant-baseline\s*:\s*central/g, 'dominant-baseline: middle');
            }
          },
        },
      }),
    },

    // 6. Convert rgba() colors to hex + opacity for Batik/FOP
    //    Batik does not support rgba() function notation in fill/stroke.
    //    It throws DOMException: "fill" property does not support function values.
    //    Convert rgba(r,g,b,a) to #rrggbb and set fill-opacity/stroke-opacity.
    {
      name: 'convertRgbaToHex',
      fn: () => {
        const RGBA_RE = /rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)/g;

        function rgbaToHex(r, g, b) {
          const toHex = (v) => {
            const hex = parseInt(v, 10).toString(16);
            return hex.length === 1 ? '0' + hex : hex;
          };
          return '#' + toHex(r) + toHex(g) + toHex(b);
        }

        function replaceRgbaInStyle(style) {
          // Track the last alpha we extract (for setting opacity)
          let lastAlpha = null;
          const replaced = style.replace(RGBA_RE, (_, r, g, b, a) => {
            lastAlpha = parseFloat(a);
            return rgbaToHex(r, g, b);
          });
          return { replaced, lastAlpha };
        }

        return {
          element: {
            enter: (node) => {
              // Handle fill/stroke attributes directly
              for (const attr of ['fill', 'stroke']) {
                if (node.attributes?.[attr]) {
                  const match = /rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)/.exec(node.attributes[attr]);
                  if (match) {
                    node.attributes[attr] = rgbaToHex(match[1], match[2], match[3]);
                    node.attributes[attr + '-opacity'] = match[4];
                  }
                }
              }
              // Handle inline style attributes
              if (node.attributes?.style) {
                const { replaced, lastAlpha } = replaceRgbaInStyle(node.attributes.style);
                if (replaced !== node.attributes.style) {
                  node.attributes.style = replaced;
                  // Remove background-color (not valid SVG)
                  node.attributes.style = node.attributes.style
                    .replace(/background-color\s*:\s*[^;]+;?\s*/g, '');
                }
              }
            },
          },
        };
      },
    },

    // 7. Fix <rect> elements missing width/height attributes
    //    Mermaid state diagrams emit bare <rect style="..."/> without
    //    dimensions. Batik (used by FOP) requires explicit width/height
    //    and throws BridgeException without them. Adding "0" makes them
    //    valid but invisible — they're just background decorations.
    {
      name: 'fixMissingRectDims',
      fn: () => ({
        element: {
          enter: (node) => {
            if (node.name !== 'rect') return;
            if (!node.attributes.width) {
              node.attributes.width = '0';
            }
            if (!node.attributes.height) {
              node.attributes.height = '0';
            }
          },
        },
      }),
    },

    // 8. Replace foreignObject with native SVG <text>
    {
      name: 'replaceForeignObject',
      fn: () => ({
        element: {
          enter: (node, parentNode) => {
            if (node.name !== 'foreignObject') return;

            const width = parseFloat(node.attributes?.width || '0');
            const height = parseFloat(node.attributes?.height || '0');

            // Remove empty placeholders (0x0 dimensions = no label)
            if (width === 0 && height === 0) {
              const parent = parentNode;
              if (parent.children) {
                const idx = parent.children.indexOf(node);
                if (idx !== -1) {
                  parent.children.splice(idx, 1);
                }
              }
              return;
            }

            // Extract label text
            const text = extractTextFromForeignObject(node);
            if (!text) {
              const parent = parentNode;
              if (parent.children) {
                const idx = parent.children.indexOf(node);
                if (idx !== -1) {
                  parent.children.splice(idx, 1);
                }
              }
              return;
            }

            // Determine text alignment from the div's style
            const textAnchor = getTextAnchor(node);

            // Compute x position based on alignment
            let xPos;
            if (textAnchor === 'start') {
              xPos = '0';
            } else {
              xPos = String(width / 2);
            }

            // Replace foreignObject with <text> in-place
            node.name = 'text';
            node.attributes = {
              x: xPos,
              y: String(height / 2),
              'text-anchor': textAnchor,
              'dominant-baseline': 'middle',
              'font-family': '"trebuchet ms", verdana, arial, sans-serif',
              'font-size': '16px',
              fill: '#333',
            };
            node.children = [
              {
                type: 'text',
                value: text,
              },
            ];
          },
        },
      }),
    },

    // 9. Remove the <style> element (now empty after inlining)
    'removeStyleElement',
  ],
};
