package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.DependencyManagement;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Parent;
import org.apache.maven.model.v4.MavenStaxReader;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only POM model backed by Maven 4's {@code maven-api-model}.
 *
 * <p>Parses a POM file using {@link MavenStaxReader} with location
 * tracking enabled. Provides typed access to dependencies, properties,
 * and parent — replacing regex-based extraction throughout the
 * workspace plugin.
 *
 * <p>For writes, static utility methods perform targeted text
 * replacement on the raw POM content, preserving formatting.
 */
final class PomModel {

    private final Model model;
    private final String content;

    private PomModel(Model model, String content) {
        this.model = model;
        this.content = content;
    }

    /**
     * Parse a POM file into a model with location tracking.
     *
     * @param pomFile path to pom.xml
     * @return parsed model
     * @throws IOException if the file cannot be read or parsed
     */
    static PomModel parse(Path pomFile) throws IOException {
        String content = Files.readString(pomFile, StandardCharsets.UTF_8);
        MavenStaxReader reader = new MavenStaxReader();
        reader.setAddLocationInformation(true);
        try {
            Model model = reader.read(new StringReader(content), true, null);
            return new PomModel(model, content);
        } catch (XMLStreamException e) {
            throw new IOException("Cannot parse " + pomFile + ": "
                    + e.getMessage(), e);
        }
    }

    /** The underlying Maven 4 model. */
    Model model() { return model; }

    /** Raw POM text for targeted editing. */
    String content() { return content; }

    // ── Reading ────────────────────────────────────────────────────

    /**
     * All dependencies from both {@code <dependencies>} and
     * {@code <dependencyManagement>} sections.
     */
    List<Dependency> allDependencies() {
        List<Dependency> result = new ArrayList<>(model.getDependencies());
        DependencyManagement mgmt = model.getDependencyManagement();
        if (mgmt != null) {
            result.addAll(mgmt.getDependencies());
        }
        return Collections.unmodifiableList(result);
    }

    /** Properties from {@code <properties>}. */
    Map<String, String> properties() {
        return model.getProperties();
    }

    /** Parent info, or null if no parent block. */
    Parent parent() {
        return model.getParent();
    }

    /** The project's own groupId (may be null if inherited). */
    String groupId() {
        String gid = model.getGroupId();
        if (gid != null) return gid;
        Parent p = model.getParent();
        return p != null ? p.getGroupId() : null;
    }

    /** The project's own artifactId. */
    String artifactId() {
        return model.getArtifactId();
    }

    /** The project's own version (may be null if inherited). */
    String version() {
        String v = model.getVersion();
        if (v != null) return v;
        Parent p = model.getParent();
        return p != null ? p.getVersion() : null;
    }

    /** Subprojects (Maven 4.1.0) or modules (Maven 4.0.0). */
    List<String> subprojects() {
        List<String> subs = model.getSubprojects();
        if (subs != null && !subs.isEmpty()) return subs;
        return model.getModules();
    }

    /**
     * BOM imports from {@code <dependencyManagement>} — dependencies
     * with {@code <type>pom</type>} and {@code <scope>import</scope>}.
     *
     * <p>Uses the Maven 4 model API for precise detection instead of
     * regex parsing (#47). Property references (e.g., {@code ${foo.version}})
     * are resolved against the POM's {@code <properties>} block.
     *
     * @return list of BOM import dependencies (unmodifiable)
     */
    List<Dependency> bomImports() {
        DependencyManagement mgmt = model.getDependencyManagement();
        if (mgmt == null) return List.of();

        Map<String, String> props = model.getProperties();
        return mgmt.getDependencies().stream()
                .filter(d -> "pom".equals(d.getType())
                        && "import".equals(d.getScope()))
                .map(d -> {
                    // Resolve ${property} in version
                    String version = d.getVersion();
                    if (version != null && version.startsWith("${")
                            && version.endsWith("}")) {
                        String propName = version.substring(2, version.length() - 1);
                        String resolved = props.get(propName);
                        if (resolved != null) {
                            return d.withVersion(resolved);
                        }
                    }
                    return d;
                })
                .toList();
    }

    // ── Writing (targeted text replacement) ────────────────────────

    /**
     * Element-order-tolerant pattern for a dependency block.
     * Matches a {@code <dependency>} containing the given
     * groupId and artifactId in any order, capturing the
     * version element's value for replacement.
     */
    private static final String DEP_ELEMENT =
            "(?:(?!</dependency>).)*?";

    /**
     * Update the version of a specific dependency identified by
     * {@code groupId:artifactId}. Handles any element ordering
     * within the dependency block.
     *
     * @param pomContent the raw POM text
     * @param groupId    dependency groupId to match
     * @param artifactId dependency artifactId to match
     * @param newVersion the version to set
     * @return updated POM text, or unchanged if no match
     */
    static String updateDependencyVersion(String pomContent,
                                           String groupId,
                                           String artifactId,
                                           String newVersion) {
        // Match a <dependency> block containing both groupId and artifactId,
        // with a <version> element anywhere inside. Element order is not
        // assumed — groupId, artifactId, type, scope, version can be in
        // any position.
        Pattern p = Pattern.compile(
                "(?s)(<dependency>" + DEP_ELEMENT +
                "<groupId>\\s*" + Pattern.quote(groupId) + "\\s*</groupId>" +
                DEP_ELEMENT +
                "<artifactId>\\s*" + Pattern.quote(artifactId) +
                "\\s*</artifactId>" +
                DEP_ELEMENT +
                "<version>)([^<]*)(</version>)");
        Matcher m = p.matcher(pomContent);
        if (m.find()) {
            return m.replaceFirst(
                    Matcher.quoteReplacement(m.group(1))
                    + Matcher.quoteReplacement(newVersion)
                    + Matcher.quoteReplacement(m.group(3)));
        }

        // Try reversed order: artifactId before groupId
        Pattern p2 = Pattern.compile(
                "(?s)(<dependency>" + DEP_ELEMENT +
                "<artifactId>\\s*" + Pattern.quote(artifactId) +
                "\\s*</artifactId>" + DEP_ELEMENT +
                "<groupId>\\s*" + Pattern.quote(groupId) + "\\s*</groupId>" +
                DEP_ELEMENT +
                "<version>)([^<]*)(</version>)");
        Matcher m2 = p2.matcher(pomContent);
        if (m2.find()) {
            return m2.replaceFirst(
                    Matcher.quoteReplacement(m2.group(1))
                    + Matcher.quoteReplacement(newVersion)
                    + Matcher.quoteReplacement(m2.group(3)));
        }

        return pomContent;
    }

    /**
     * Update a version property value in the POM content.
     * Delegates to {@link ReleaseSupport#updateVersionProperty}.
     *
     * @param pomContent   the raw POM text
     * @param propertyName the property name (e.g., "tinkar-core.version")
     * @param newValue     the new property value
     * @return updated POM text
     */
    static String updateProperty(String pomContent,
                                  String propertyName,
                                  String newValue) {
        return ReleaseSupport.updateVersionProperty(
                pomContent, propertyName, newValue);
    }

    /**
     * Update the parent version in a POM's {@code <parent>} block.
     *
     * @param pomContent      the raw POM text
     * @param parentArtifactId the parent artifactId to match
     * @param newVersion      the new version to set
     * @return updated POM text, or unchanged if no match
     */
    static String updateParentVersion(String pomContent,
                                       String parentArtifactId,
                                       String newVersion) {
        // Element-order-tolerant: match parent block with the given
        // artifactId anywhere inside, then replace the version value.
        Pattern p = Pattern.compile(
                "(?s)(<parent>\\s*" +
                "(?:(?!</parent>).)*?" +
                "<artifactId>\\s*" + Pattern.quote(parentArtifactId) +
                "\\s*</artifactId>" +
                "(?:(?!</parent>).)*?" +
                "<version>)([^<]*)(</version>)");
        Matcher m = p.matcher(pomContent);
        if (m.find()) {
            return m.replaceFirst(
                    Matcher.quoteReplacement(m.group(1))
                    + Matcher.quoteReplacement(newVersion)
                    + Matcher.quoteReplacement(m.group(3)));
        }
        return pomContent;
    }
}
