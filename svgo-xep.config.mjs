// svgo-xep.config.mjs — Fix Mermaid SVGs for RenderX XEP
//
// XEP's SVG renderer does not support:
//   1. <foreignObject> — Mermaid uses these for all labels (flowchart,
//      state, ER). XEP silently drops them, so labels disappear.
//   2. HSL color values — Mermaid's theme uses hsl(h, s%, l%) in CSS.
//      XEP ignores them, causing strokes and fills to vanish.
//
// This config applies four transformations in order:
//   1. inlineStyles:          CSS rules → element-level style attributes
//   2. convertHslToHex:       hsl(h, s%, l%) → #rrggbb
//   3. replaceForeignObject:  <foreignObject> → <text>
//   4. removeStyleElement:    drop the (now-empty) <style> block
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

    // 2. Convert HSL colors to hex in both <style> text and inline styles
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

    // 3. Replace foreignObject with native SVG <text>
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
              'dominant-baseline': 'central',
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

    // 4. Remove the <style> element (now empty after inlining)
    'removeStyleElement',
  ],
};
