# frozen_string_literal: true

#
# Koncept Inline Macro Extension for AsciidoctorJ
# ────────────────────────────────────────────────────────────────
# Renders formally identified Koncepts with distinctive visual
# styling across all IKE documentation backends (HTML5, PDF, DocBook5).
#
# Usage in AsciiDoc source:
#
#   koncept:Infectious Pneumonia[]
#   koncept:Intraocular Pressure[url=https://komet.ike.dev/koncept/abc-123]
#   koncept:Diabetes mellitus[uuid=73211009]
#   koncept:Hypertension[uuid=38341003,url=https://komet.ike.dev/koncept/38341003]
#
# Rendering by backend:
#   HTML5:    <span class="koncept"> with styled sub-spans (K, brackets, name)
#             Linked variant wraps in <a class="koncept-link">
#   PDF:      Prawn inline formatting via roles (koncept-k, koncept-name)
#   DocBook5: <phrase role="koncept"> with nested markup
#
# Copyright 2025 IKE Community — Apache License 2.0
#

require 'asciidoctor/extensions'

Asciidoctor::Extensions.register do
  inline_macro do
    named :koncept
    name_positional_attributes 'url', 'uuid'
    # Match target that may contain spaces, dots, parentheses
    # e.g., koncept:Diabetes mellitus (disorder)[]
    resolve_attributes true

    process do |parent, target, attrs|
      url  = attrs['url']
      uuid = attrs['uuid']
      backend = parent.document.backend

      case backend
      when 'html5'
        render_html5(parent, target, url, uuid)
      when 'pdf'
        render_pdf(parent, target, url, uuid)
      when 'docbook5', 'docbook'
        render_docbook(parent, target, url, uuid)
      else
        # Fallback: plain text with K[] notation
        create_inline parent, :quoted, %(K[#{target}])
      end
    end

    private

    def render_html5(parent, target, url, uuid)
      k_span       = '<span class="koncept-k">K</span>'
      bracket_open  = '<span class="koncept-bracket">[</span>'
      bracket_close = '<span class="koncept-bracket">]</span>'
      name_span     = %(<span class="koncept-name">#{target}</span>)

      inner = %(#{k_span}#{bracket_open}#{name_span}#{bracket_close})

      title_text = %(Koncept: #{target}#{uuid ? " (#{uuid})" : ''})

      if url
        html = %(<a href="#{url}" class="koncept-link" title="#{title_text}">#{inner}</a>)
      else
        data_attr = uuid ? %( data-uuid="#{uuid}") : ''
        html = %(<span class="koncept"#{data_attr} title="#{title_text}">#{inner}</span>)
      end

      create_inline parent, :quoted, html
    end

    def render_pdf(parent, target, url, uuid)
      # AsciidoctorJ PDF supports a subset of inline HTML-like tags:
      #   <font>, <color>, <b>, <i>, <u>, <sub>, <sup>, <a>
      # We use <color> and <b> for visual distinction.
      # Small-caps are not supported in Prawn, so we use ALL-CAPS at
      # reduced size via the theme's role-based font-size override.

      k_part       = '<color rgb="#2563EB"><b>K</b></color>'
      bracket_open  = '<color rgb="#94A3B8">[</color>'
      bracket_close = '<color rgb="#94A3B8">]</color>'
      name_part     = %(<color rgb="#1E3A5F">#{target.upcase}</color>)

      inner = %(#{k_part}#{bracket_open}#{name_part}#{bracket_close})

      if url
        # Prawn PDF supports <a> tags in inline content
        inner = %(<a href="#{url}">#{inner}</a>)
      end

      create_inline parent, :quoted, inner
    end

    def render_docbook(parent, target, url, uuid)
      # DocBook5: use <phrase> with role for downstream XSLT styling.
      # The ike-fo.xsl customization layer can match on role="koncept".
      attrs_xml = uuid ? %( xml:id="koncept-#{uuid}") : ''

      if url
        docbook = %(<link xl:href="#{url}"><phrase role="koncept"#{attrs_xml}>K[#{target}]</phrase></link>)
      else
        docbook = %(<phrase role="koncept"#{attrs_xml}>K[#{target}]</phrase>)
      end

      create_inline parent, :quoted, docbook
    end
  end
end
