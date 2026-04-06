package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;
import network.ike.workspace.Component;
import network.ike.workspace.Dependency;
import network.ike.workspace.PublishedArtifactSet;
import network.ike.workspace.WorkspaceGraph;
import org.apache.maven.api.model.Parent;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Align inter-component dependency versions in POM files to match the
 * current workspace component versions.
 *
 * <p>This is the {@code ws:align} goal. For each component on disk, it
 * scans POM dependency declarations. When a dependency's groupId matches
 * another workspace component and the declared version does not match
 * that component's current POM version, the dependency version is
 * updated.
 *
 * <p>Property-based versions (e.g., {@code <ike-bom.version>}) are
 * updated via {@code ReleaseSupport.updateVersionProperty()}. Direct
 * {@code <version>} tags in dependency blocks are updated via text
 * replacement.
 *
 * <pre>{@code
 * mvn ws:align                    # apply changes
 * mvn ws:align -DdryRun=true      # report only
 * }</pre>
 */
@Mojo(name = "align", requiresProject = false, threadSafe = true)
public class WsAlignMojo extends AbstractWorkspaceMojo {

    /**
     * When true, report changes without writing to POM files.
     */
    @Parameter(property = "dryRun", defaultValue = "false")
    private boolean dryRun;

    /** Creates this goal instance. */
    public WsAlignMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("");
        getLog().info("IKE Workspace Align — synchronize inter-component dependency versions");
        getLog().info("══════════════════════════════════════════════════════════════");

        if (dryRun) {
            getLog().info("  (dry run — no files will be modified)");
            getLog().info("");
        }

        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();

        // Build lookup: groupId:artifactId → (component name, current POM version)
        Map<String, ComponentVersion> artifactIndex = buildArtifactIndex(graph, root);

        int totalChanges = 0;
        List<String> changedComponents = new ArrayList<>();
        List<AlignChange> reportChanges = new ArrayList<>();

        for (Map.Entry<String, Component> entry : graph.manifest().components().entrySet()) {
            String name = entry.getKey();
            Component component = entry.getValue();
            File componentDir = new File(root, name);

            if (!new File(componentDir, "pom.xml").exists()) {
                getLog().debug("  " + name + ": not cloned — skipping");
                continue;
            }

            // Find all POM files in this component
            List<File> pomFiles;
            try {
                pomFiles = ReleaseSupport.findPomFiles(componentDir);
            } catch (MojoExecutionException e) {
                getLog().warn("  " + name + ": could not scan POM files — "
                        + e.getMessage());
                continue;
            }

            // Also use the declared depends-on to find version-property hints
            Map<String, String> versionPropertyMap = new LinkedHashMap<>();
            for (Dependency dep : component.dependsOn()) {
                if (dep.versionProperty() != null && !dep.versionProperty().isEmpty()) {
                    Component target = graph.manifest().components().get(dep.component());
                    if (target != null && target.groupId() != null
                            && !target.groupId().isEmpty()) {
                        versionPropertyMap.put(dep.component(), dep.versionProperty());
                    }
                }
            }

            int componentChanges = 0;

            for (File pomFile : pomFiles) {
                int changes = alignPomDependencies(
                        name, pomFile, artifactIndex, versionPropertyMap,
                        componentDir, graph, reportChanges);
                componentChanges += changes;
            }

            if (componentChanges > 0) {
                totalChanges += componentChanges;
                changedComponents.add(name);
            }
        }

        // --- Parent version alignment (via Maven 4 Model API) ---
        for (Map.Entry<String, Component> entry : graph.manifest().components().entrySet()) {
            String name = entry.getKey();
            Component component = entry.getValue();
            String parentComponentName = component.parent();
            if (parentComponentName == null) continue;

            Component parentComponent = graph.manifest().components().get(parentComponentName);
            if (parentComponent == null || parentComponent.version() == null) continue;

            File componentDir = new File(root, name);
            Path pomPath = componentDir.toPath().resolve("pom.xml");
            if (!Files.exists(pomPath)) continue;

            try {
                PomModel pom = PomModel.parse(pomPath);
                Parent parentInfo = pom.parent();
                if (parentInfo == null) continue;

                String expectedVersion = parentComponent.version();
                String currentVersion = parentInfo.getVersion();
                if (currentVersion == null
                        || expectedVersion.equals(currentVersion)) {
                    continue;
                }

                String parentAid = parentInfo.getArtifactId();
                if (dryRun) {
                    getLog().info("  " + name + ": parent " + parentAid
                            + " " + currentVersion + " → " + expectedVersion
                            + " (dry run)");
                } else {
                    String updated = PomModel.updateParentVersion(
                            pom.content(), parentAid, expectedVersion);
                    Files.writeString(pomPath, updated, StandardCharsets.UTF_8);
                    getLog().info("  " + name + ": parent " + parentAid
                            + " " + currentVersion + " → " + expectedVersion);

                    // Also update submodule POMs that reference the same parent
                    List<File> subPoms = ReleaseSupport.findPomFiles(componentDir);
                    for (File subPom : subPoms) {
                        if (subPom.toPath().equals(pomPath)) continue;
                        String subContent = Files.readString(
                                subPom.toPath(), StandardCharsets.UTF_8);
                        String subUpdated = PomModel.updateParentVersion(
                                subContent, parentAid, expectedVersion);
                        if (!subUpdated.equals(subContent)) {
                            Files.writeString(subPom.toPath(), subUpdated,
                                    StandardCharsets.UTF_8);
                        }
                    }
                }
                reportChanges.add(new AlignChange(
                        name, "pom.xml", "parent:" + parentAid,
                        currentVersion, expectedVersion));
                totalChanges++;
                if (!changedComponents.contains(name)) {
                    changedComponents.add(name);
                }
            } catch (IOException e) {
                getLog().warn("  " + name + ": could not align parent version — "
                        + e.getMessage());
            }
        }

        // --- Summary ---
        getLog().info("");
        if (totalChanges == 0) {
            getLog().info("  All inter-component dependency and parent versions are aligned  ✓");
        } else if (dryRun) {
            getLog().info("  " + totalChanges + " version(s) would be updated across "
                    + changedComponents.size() + " component(s)");
            getLog().info("  Run without -DdryRun to apply changes.");
        } else {
            getLog().info("  Updated " + totalChanges + " version(s) across "
                    + changedComponents.size() + " component(s)");
        }
        getLog().info("");

        // --- Structured markdown report ---
        appendReport("ws:align", buildMarkdownReport(
                totalChanges, changedComponents, reportChanges));
    }

    /**
     * Build a structured markdown report from collected alignment changes.
     */
    private String buildMarkdownReport(int totalChanges,
                                        List<String> changedComponents,
                                        List<AlignChange> changes) {
        StringBuilder md = new StringBuilder();

        if (totalChanges == 0) {
            md.append("All inter-component dependency and parent versions are aligned.\n");
            return md.toString();
        }

        if (dryRun) {
            md.append("**Dry run** — ").append(totalChanges)
              .append(" version(s) would be updated across ")
              .append(changedComponents.size()).append(" component(s).\n\n");
        } else {
            md.append("Updated ").append(totalChanges)
              .append(" version(s) across ")
              .append(changedComponents.size()).append(" component(s).\n\n");
        }

        md.append("| Component | POM | Artifact | From | To |\n");
        md.append("|-----------|-----|----------|------|----|\n");
        for (AlignChange c : changes) {
            md.append("| ").append(c.component)
              .append(" | ").append(c.pomRelPath)
              .append(" | `").append(c.artifact).append('`')
              .append(" | ").append(c.fromVersion)
              .append(" | ").append(c.toVersion)
              .append(" |\n");
        }

        return md.toString();
    }

    /** A single version alignment change for the report. */
    private record AlignChange(String component, String pomRelPath,
                                String artifact, String fromVersion,
                                String toVersion) {}

    // ── Artifact index ───────────────────────────────────────────────

    /**
     * Build an index from {@code groupId:artifactId} to (component name,
     * current POM version) for all cloned workspace components.
     *
     * <p>Uses {@link PublishedArtifactSet#scan} to discover every
     * artifact each component publishes, so components sharing a
     * groupId (e.g., {@code dev.ikm.ike}) are correctly distinguished
     * by artifactId.
     */
    private Map<String, ComponentVersion> buildArtifactIndex(
            WorkspaceGraph graph, File root) throws MojoExecutionException {
        Map<String, ComponentVersion> index = new LinkedHashMap<>();

        for (Map.Entry<String, Component> entry : graph.manifest().components().entrySet()) {
            String name = entry.getKey();
            File componentDir = new File(root, name);

            if (!new File(componentDir, "pom.xml").exists()) {
                continue;
            }

            String pomVersion;
            try {
                pomVersion = ReleaseSupport.readPomVersion(
                        new File(componentDir, "pom.xml"));
            } catch (MojoExecutionException e) {
                getLog().warn("  " + name + ": could not read POM version — "
                        + e.getMessage());
                continue;
            }

            Set<PublishedArtifactSet.Artifact> published;
            try {
                published = PublishedArtifactSet.scan(componentDir.toPath());
            } catch (IOException e) {
                getLog().warn("  " + name + ": could not scan published artifacts — "
                        + e.getMessage());
                continue;
            }

            ComponentVersion cv = new ComponentVersion(name, pomVersion);
            for (PublishedArtifactSet.Artifact artifact : published) {
                String key = artifact.groupId() + ":" + artifact.artifactId();
                index.put(key, cv);
            }
        }

        return index;
    }

    // ── POM dependency alignment ────────────────────────────────────

    /**
     * Scan a single POM file for dependencies whose {@code groupId:artifactId}
     * matches a workspace component's published artifact, and update
     * mismatched versions.
     *
     * <p>Uses Maven 4's {@link PomModel} for reading dependency coordinates
     * (no regex for extraction). Writes use targeted text replacement via
     * {@link PomModel#updateDependencyVersion} to preserve formatting.
     *
     * @return number of changes made (or that would be made in dry-run)
     */
    private int alignPomDependencies(String ownerName, File pomFile,
                                     Map<String, ComponentVersion> artifactIndex,
                                     Map<String, String> versionPropertyMap,
                                     File componentDir, WorkspaceGraph graph,
                                     List<AlignChange> reportChanges)
            throws MojoExecutionException {
        PomModel pom;
        try {
            pom = PomModel.parse(pomFile.toPath());
        } catch (IOException e) {
            getLog().debug("  " + ownerName + ": skipping "
                    + pomFile.getName() + " (empty or unparseable)");
            return 0;
        }

        String updated = pom.content();
        int changes = 0;

        // Iterate all dependencies using the Maven 4 Model API
        for (org.apache.maven.api.model.Dependency dep : pom.allDependencies()) {
            String depGroupId = dep.getGroupId();
            String depArtifactId = dep.getArtifactId();
            String currentVersion = dep.getVersion();

            if (depGroupId == null || depArtifactId == null
                    || currentVersion == null) {
                continue;
            }

            String key = depGroupId + ":" + depArtifactId;
            ComponentVersion target = artifactIndex.get(key);

            // Skip if not a workspace artifact or self-reference
            if (target == null || target.name.equals(ownerName)) {
                continue;
            }

            if (currentVersion.startsWith("${") && currentVersion.endsWith("}")) {
                // Property-based version — resolve via model properties
                String propName = currentVersion.substring(2,
                        currentVersion.length() - 1);
                String propValue = pom.properties().get(propName);
                if (propValue != null && !propValue.equals(target.version)) {
                    String relPath = componentDir.toPath().relativize(
                            pomFile.toPath()).toString();
                    getLog().info("  " + ownerName + " (" + relPath
                            + "): property <" + propName + "> "
                            + propValue + " → " + target.version);
                    reportChanges.add(new AlignChange(
                            ownerName, relPath,
                            "property:" + propName,
                            propValue, target.version));
                    updated = PomModel.updateProperty(
                            updated, propName, target.version);
                    changes++;
                }
            } else if (!currentVersion.equals(target.version)) {
                // Direct version mismatch — targeted text replacement
                String relPath = componentDir.toPath().relativize(
                        pomFile.toPath()).toString();
                getLog().info("  " + ownerName + " (" + relPath + "): "
                        + key + " " + currentVersion
                        + " → " + target.version);
                reportChanges.add(new AlignChange(
                        ownerName, relPath, key,
                        currentVersion, target.version));
                updated = PomModel.updateDependencyVersion(
                        updated, depGroupId, depArtifactId, target.version);
                changes++;
            }
        }

        // Handle version-property updates declared in depends-on.
        // Look up the target component's version by name (via the
        // artifact index), not by groupId — avoids the collision.
        for (Map.Entry<String, String> vpEntry : versionPropertyMap.entrySet()) {
            String targetComponent = vpEntry.getKey();
            String versionProperty = vpEntry.getValue();

            ComponentVersion cv = findComponentVersion(
                    targetComponent, artifactIndex, workspaceRoot());
            if (cv == null) continue;

            // Read current property value from the model
            String currentValue = pom.properties().get(versionProperty);
            if (currentValue != null && !currentValue.equals(cv.version)) {
                String relPath = componentDir.toPath().relativize(
                        pomFile.toPath()).toString();
                getLog().info("  " + ownerName + " (" + relPath
                        + "): property <" + versionProperty + "> "
                        + currentValue + " → " + cv.version);
                reportChanges.add(new AlignChange(
                        ownerName, relPath,
                        "property:" + versionProperty,
                        currentValue, cv.version));
                updated = PomModel.updateProperty(
                        updated, versionProperty, cv.version);
                changes++;
            }
        }

        // Write if changed
        if (changes > 0 && !dryRun && !updated.equals(pom.content())) {
            try {
                Files.writeString(pomFile.toPath(), updated,
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new MojoExecutionException(
                        "Failed to write " + pomFile + ": "
                        + e.getMessage(), e);
            }
        }

        return changes;
    }

    /**
     * Find a component's version by scanning its published artifacts
     * and looking them up in the artifact index. Matches by component
     * name (not groupId), so it handles groupId collisions.
     */
    private ComponentVersion findComponentVersion(
            String componentName,
            Map<String, ComponentVersion> artifactIndex, File root) {
        File componentDir = new File(root, componentName);
        if (!new File(componentDir, "pom.xml").exists()) {
            return null;
        }
        try {
            Set<PublishedArtifactSet.Artifact> published =
                    PublishedArtifactSet.scan(componentDir.toPath());
            for (PublishedArtifactSet.Artifact artifact : published) {
                String key = artifact.groupId() + ":" + artifact.artifactId();
                ComponentVersion cv = artifactIndex.get(key);
                if (cv != null && cv.name.equals(componentName)) {
                    return cv;
                }
            }
        } catch (IOException e) {
            // Fall through
        }
        return null;
    }

    // ── Internal record ─────────────────────────────────────────────

    /**
     * Associates a component name with its current POM version.
     */
    private record ComponentVersion(String name, String version) {}
}
