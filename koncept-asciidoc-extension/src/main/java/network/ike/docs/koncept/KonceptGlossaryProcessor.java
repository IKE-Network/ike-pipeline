package network.ike.docs.koncept;

import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.Postprocessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * AsciidoctorJ postprocessor that generates a "Referenced Koncepts" glossary
 * section at the end of the document.
 * <p>
 * Uses a {@link Postprocessor} rather than a Treeprocessor because inline
 * macros (including {@link KonceptInlineMacro}) are resolved during the
 * conversion phase, which runs after tree processing.
 * <p>
 * The glossary is built from the koncept registry populated by
 * {@link KonceptInlineMacro} during document conversion. Each referenced
 * koncept gets a single glossary entry containing:
 * <ul>
 *   <li>An anchor target matching the inline SVG badge links</li>
 *   <li>Natural language definition</li>
 *   <li>Description logic axiom in Unicode notation</li>
 *   <li>Optional SNOMED CT identifier</li>
 *   <li>Optional OWL IRI</li>
 * </ul>
 * <p>
 * Definitions are resolved via {@link KonceptDefinitionSource}. The source
 * is configured by document attributes:
 * <ul>
 *   <li>{@code :koncept-definitions-file:} — filesystem path to YAML</li>
 *   <li>{@code :koncept-definitions-classpath:} — classpath resource path</li>
 *   <li>Default: {@code /koncepts.yml} on classpath</li>
 * </ul>
 */
public class KonceptGlossaryProcessor extends Postprocessor {

    private static final Logger LOG = LoggerFactory.getLogger(KonceptGlossaryProcessor.class);

    private static final String DEFAULT_CLASSPATH_RESOURCE = "/koncepts.yml";
    private static final String ATTR_DEFS_FILE = "koncept-definitions-file";
    private static final String ATTR_DEFS_CLASSPATH = "koncept-definitions-classpath";

    @Override
    public String process(Document document, String output) {
        // The pdf backend (asciidoctorj-pdf/Prawn) produces binary PDF output,
        // not HTML or DocBook text. The Postprocessor cannot modify it and
        // attempting to do so causes a JRuby type conversion error.
        String backend = document.getAttribute("backend", "html5").toString();
        if ("pdf".equals(backend)) {
            LOG.debug("Skipping glossary generation for pdf backend (Prawn)");
            return output;
        }

        // Retrieve the registry populated by KonceptInlineMacro
        Map<String, KonceptEntry> rawRegistry = KonceptInlineMacro.removeRegistry(document);
        if (rawRegistry == null || rawRegistry.isEmpty()) {
            LOG.debug("No koncept references found; skipping glossary generation");
            return output;
        }

        Map<String, KonceptEntry> registry = new TreeMap<>(rawRegistry);
        LOG.info("Generating glossary for {} referenced koncepts", registry.size());

        // Resolve the definition source
        KonceptDefinitionSource defSource = resolveDefinitionSource(document);

        // Backend-aware glossary rendering
        if ("docbook5".equals(backend) || "docbook".equals(backend)) {
            String glossaryDocbook = buildGlossaryDocbook(registry, defSource);
            int bookClose = output.lastIndexOf("</book>");
            if (bookClose >= 0) {
                return output.substring(0, bookClose) + glossaryDocbook + output.substring(bookClose);
            }
            return output + glossaryDocbook;
        }

        // HTML glossary (works for html5, pdf, and CSS-based renderers)
        String glossaryHtml = buildGlossaryHtml(registry, defSource);

        // Append before closing </body> if present, otherwise just append
        int bodyClose = output.lastIndexOf("</body>");
        if (bodyClose >= 0) {
            return output.substring(0, bodyClose) + glossaryHtml + output.substring(bodyClose);
        }
        return output + glossaryHtml;
    }

    /**
     * Determine which definition source to use based on document attributes.
     */
    private KonceptDefinitionSource resolveDefinitionSource(Document document) {
        // Check for explicit filesystem path
        Object filePath = document.getAttribute(ATTR_DEFS_FILE);
        if (filePath != null && !filePath.toString().isBlank()) {
            LOG.info("Loading koncept definitions from file: {}", filePath);
            return KonceptDefinitionSource.fromFile(filePath.toString());
        }

        // Check for explicit classpath resource
        Object cpPath = document.getAttribute(ATTR_DEFS_CLASSPATH);
        if (cpPath != null && !cpPath.toString().isBlank()) {
            LOG.info("Loading koncept definitions from classpath: {}", cpPath);
            return KonceptDefinitionSource.fromClasspath(cpPath.toString());
        }

        // Default
        LOG.info("Loading koncept definitions from default classpath: {}",
                DEFAULT_CLASSPATH_RESOURCE);
        return KonceptDefinitionSource.fromClasspath(DEFAULT_CLASSPATH_RESOURCE);
    }

    /**
     * Build the glossary section as complete HTML.
     */
    private String buildGlossaryHtml(
            Map<String, KonceptEntry> registry,
            KonceptDefinitionSource defSource) {

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"koncept-glossary-section\">\n");
        html.append("<h2 id=\"_referenced_koncepts\">Referenced Koncepts</h2>\n");
        html.append("<dl class=\"koncept-definitions\">\n");

        for (Map.Entry<String, KonceptEntry> entry : registry.entrySet()) {
            String id = entry.getKey();
            KonceptEntry konceptEntry = entry.getValue();
            Optional<KonceptDefinition> defOpt = defSource.lookup(id);

            String label = defOpt
                    .map(KonceptDefinition::label)
                    .orElse(KonceptInlineMacro.splitCamelCase(id));

            html.append("  <dt id=\"koncept-").append(escapeHtml(id)).append("\">");
            html.append("<strong>").append(escapeHtml(label)).append("</strong>");

            // Show reference count
            if (konceptEntry.getRefCount() > 1) {
                html.append(" <span class=\"koncept-ref-count\">(")
                    .append(konceptEntry.getRefCount())
                    .append(" references)</span>");
            }
            html.append("</dt>\n");

            html.append("  <dd>\n");

            if (defOpt.isPresent()) {
                KonceptDefinition def = defOpt.get();

                // Natural language definition
                if (def.definition() != null) {
                    html.append("    <p class=\"koncept-def-text\">")
                        .append(escapeHtml(def.definition()))
                        .append("</p>\n");
                }

                // Description logic axiom
                if (def.axiom() != null) {
                    html.append("    <p class=\"koncept-axiom\"><code>")
                        .append(escapeHtml(def.axiom()))
                        .append("</code></p>\n");
                }

                // Terminology identifiers
                if (def.sctid() != null || def.iri() != null) {
                    html.append("    <p class=\"koncept-ids\">");
                    if (def.sctid() != null) {
                        html.append("<span class=\"koncept-sctid\">SCTID: ")
                            .append(escapeHtml(def.sctid()))
                            .append("</span>");
                    }
                    if (def.iri() != null) {
                        if (def.sctid() != null) html.append(" | ");
                        html.append("<span class=\"koncept-iri\">IRI: ")
                            .append(escapeHtml(def.iri()))
                            .append("</span>");
                    }
                    html.append("</p>\n");
                }
            } else {
                html.append("    <p class=\"koncept-def-missing\">")
                    .append("<em>Definition not available.</em></p>\n");
            }

            html.append("  </dd>\n");
        }

        html.append("</dl>\n");
        html.append("</div>\n");

        return html.toString();
    }

    /**
     * Build the glossary section as DocBook markup.
     */
    private String buildGlossaryDocbook(
            Map<String, KonceptEntry> registry,
            KonceptDefinitionSource defSource) {

        StringBuilder db = new StringBuilder();
        db.append("<glossary xmlns=\"http://docbook.org/ns/docbook\" version=\"5.0\">\n");
        db.append("  <title>Referenced Koncepts</title>\n");

        for (Map.Entry<String, KonceptEntry> entry : registry.entrySet()) {
            String id = entry.getKey();
            Optional<KonceptDefinition> defOpt = defSource.lookup(id);

            String label = defOpt
                    .map(KonceptDefinition::label)
                    .orElse(KonceptInlineMacro.splitCamelCase(id));

            db.append("  <glossentry xml:id=\"koncept-").append(escapeXml(id)).append("\">\n");
            db.append("    <glossterm>").append(escapeXml(label)).append("</glossterm>\n");
            db.append("    <glossdef>\n");

            if (defOpt.isPresent()) {
                KonceptDefinition def = defOpt.get();
                if (def.definition() != null) {
                    db.append("      <para>").append(escapeXml(def.definition())).append("</para>\n");
                }
                if (def.axiom() != null) {
                    db.append("      <para><literal>").append(escapeXml(def.axiom())).append("</literal></para>\n");
                }
                if (def.sctid() != null) {
                    db.append("      <para>SCTID: ").append(escapeXml(def.sctid())).append("</para>\n");
                }
            } else {
                db.append("      <para>Definition not available.</para>\n");
            }

            db.append("    </glossdef>\n");
            db.append("  </glossentry>\n");
        }

        db.append("</glossary>\n");
        return db.toString();
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
