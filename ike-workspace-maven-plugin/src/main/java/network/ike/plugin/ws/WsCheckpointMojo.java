package network.ike.plugin.ws;

import network.ike.plugin.ReleaseNotesSupport;
import network.ike.plugin.ReleaseSupport;

import network.ike.workspace.Component;
import network.ike.workspace.WorkspaceGraph;
import network.ike.plugin.ws.vcs.VcsOperations;
import network.ike.plugin.ws.vcs.VcsState;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;

/**
 * Create a workspace checkpoint — tag every component at its current HEAD
 * and record the snapshot in a YAML manifest.
 *
 * <p>A checkpoint records the current state of the workspace for reproduction.
 * It is not a build or a release — no POM version changes, no compilation,
 * no deployment. TeamCity watches for checkpoint tags on the workspace repo
 * and handles CI.
 *
 * <p>Each component is tagged in topological order (dependencies before
 * dependents). After all components are tagged, a YAML file recording
 * the SHAs, versions, and branches is committed and tagged in the
 * workspace aggregator repo.
 *
 * <pre>{@code
 * mvn ws:checkpoint                          # auto-derived name
 * mvn ws:checkpoint -Dname=sprint-42         # explicit name
 * mvn ws:checkpoint                          # dry-run (default)
 * mvn ws:checkpoint-apply                    # execute
 * }</pre>
 *
 * @see CheckpointSupport the per-component tagging engine
 */
@Mojo(name = "checkpoint", requiresProject = false, threadSafe = true)
public class WsCheckpointMojo extends AbstractWorkspaceMojo {

    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter COMPACT_UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneOffset.UTC);

    /**
     * Checkpoint name. Used in the YAML filename and tag names.
     * If omitted, auto-derived from the workspace branch and a compact
     * UTC timestamp ({@code <branch>-<yyyyMMdd>-<HHmmss>}).
     */
    @Parameter(property = "name")
    String name;

    /**
     * Show what the checkpoint would do without creating tags or writing
     * files. Set automatically by {@code ws:checkpoint} (bare goal is
     * dry-run; use {@code ws:checkpoint-apply} to execute).
     */
    @Parameter(property = "dryRun", defaultValue = "true")
    boolean dryRun;

    /**
     * GitHub repository for issue tracking, used to snapshot active
     * issues into the checkpoint's testing context.
     */
    @Parameter(property = "issueRepo", defaultValue = "IKE-Network/ike-issues")
    String issueRepo;

    /**
     * Milestone name to snapshot for testing context. If omitted,
     * looks for an open milestone matching the workspace's primary
     * component (first component in manifest) in the form
     * {@code <artifactId> v<version>} where version is the current
     * SNAPSHOT stripped of the suffix.
     */
    @Parameter(property = "milestone")
    String milestone;

    /** Creates this goal instance. */
    public WsCheckpointMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();

        if (name == null || name.isBlank()) {
            name = deriveCheckpointName(root);
        }

        String wsTagName = "checkpoint/" + name;
        String timestamp = ISO_UTC.format(Instant.now());
        String author = resolveAuthor(root);

        getLog().info("");
        getLog().info(header("Checkpoint"));
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("  Name:   " + name);
        getLog().info("  Tag:    " + wsTagName);
        getLog().info("  Time:   " + timestamp);
        getLog().info("  Author: " + author);
        if (dryRun) {
            getLog().info("  Mode:   DRY RUN — no tags, no files written");
        }
        getLog().info("");

        // ── Tag each component in dependency order ────────────────────
        List<ComponentSnapshot> snapshots = new ArrayList<>();
        List<String> absentComponents = new ArrayList<>();

        List<String> ordered = graph.topologicalSort(
                new LinkedHashSet<>(graph.manifest().components().keySet()));

        for (String compName : ordered) {
            Component component = graph.manifest().components().get(compName);
            File dir = new File(root, compName);
            File gitDir = new File(dir, ".git");

            if (!gitDir.exists()) {
                absentComponents.add(compName);
                getLog().info("  - " + compName + " [absent — skipped]");
                continue;
            }

            String branch   = gitBranch(dir);
            String sha      = gitFullSha(dir);
            String shortSha = gitShortSha(dir);
            String version  = readVersion(dir);

            var ct = graph.manifest().componentTypes().get(component.type());
            boolean composite = ct != null
                    && "composite".equals(ct.checkpointMechanism());

            if (dryRun) {
                getLog().info(Ansi.green("  ✓ ") + compName
                        + " [" + shortSha + "] " + branch
                        + " (" + version + ")");
                CheckpointSupport.dryRun(dir, wsTagName, getLog());
                snapshots.add(new ComponentSnapshot(
                        compName, sha, shortSha, branch,
                        version, false, component.type(), composite));
            } else {
                CheckpointSupport.checkpoint(dir, wsTagName, getLog());
                getLog().info(Ansi.green("  ✓ ") + compName
                        + " [" + shortSha + "] → " + wsTagName);
                snapshots.add(new ComponentSnapshot(
                        compName, sha, shortSha, branch,
                        version, false, component.type(), composite));
            }
        }

        // ── Build checkpoint YAML ──────────────────────────────────────
        String yamlContent = buildCheckpointYaml(
                name, timestamp, author,
                graph.manifest().schemaVersion(),
                snapshots, absentComponents);

        // ── Append testing context from milestone ─────────────────────
        String testingContextYaml = snapshotTestingContext(graph);
        if (testingContextYaml != null) {
            yamlContent = yamlContent + "\n" + testingContextYaml;
        }

        if (dryRun) {
            getLog().info("");
            getLog().info("[DRY RUN] Checkpoint file would be written to:");
            getLog().info("[DRY RUN]   checkpoints/" + checkpointFileName(name));
            getLog().info("");
            getLog().info("[DRY RUN] Contents:");
            yamlContent.lines().forEach(line ->
                    getLog().info("[DRY RUN]   " + line));
            getLog().info("");
            return;
        }

        // ── Write checkpoint file ──────────────────────────────────────
        Path checkpointsDir = root.toPath().resolve("checkpoints");
        try {
            Files.createDirectories(checkpointsDir);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Cannot create checkpoints directory", e);
        }
        Path checkpointFile = checkpointsDir.resolve(checkpointFileName(name));
        try {
            Files.writeString(checkpointFile, yamlContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failed to write " + checkpointFile, e);
        }

        // ── Tag and push workspace aggregator repo ──────────────────
        File wsGitDir = new File(root, ".git");
        if (wsGitDir.exists()) {
            ReleaseSupport.exec(root, getLog(),
                    "git", "add", "checkpoints/" + checkpointFileName(name));
            ReleaseSupport.exec(root, getLog(),
                    "git", "commit", "-m",
                    "checkpoint: " + name);
            ReleaseSupport.exec(root, getLog(),
                    "git", "tag", "-a", wsTagName,
                    "-m", "Workspace checkpoint " + name);

            boolean hasOrigin = ReleaseSupport.hasRemote(root, "origin");
            if (hasOrigin) {
                ReleaseSupport.exec(root, getLog(),
                        "git", "push", "origin", wsTagName);
                ReleaseSupport.exec(root, getLog(),
                        "git", "push", "origin",
                        ReleaseSupport.currentBranch(root));
                getLog().info("  Workspace tag pushed: " + wsTagName);
            }
        }

        // VCS bridge: write state file after checkpoint
        for (var entry : graph.manifest().components().entrySet()) {
            File compDir = new File(root, entry.getKey());
            if (new File(compDir, ".git").exists()
                    && VcsState.isIkeManaged(compDir.toPath())) {
                VcsOperations.writeVcsState(compDir, VcsState.ACTION_CHECKPOINT);
            }
        }
        if (VcsState.isIkeManaged(root.toPath())) {
            VcsOperations.writeVcsState(root, VcsState.ACTION_CHECKPOINT);
        }

        getLog().info("");
        getLog().info("  Checkpoint: " + checkpointFile);
        getLog().info("  Components: " + snapshots.size()
                + " | Absent: " + absentComponents.size());
        getLog().info("");

        appendReport("ws:checkpoint",
                buildCheckpointMarkdownReport(snapshots, absentComponents));
    }

    // ── Per-component checkpoint (overridable for tests) ──────────────

    /**
     * Tag a single component at its current HEAD. Override in tests
     * to substitute a lighter-weight simulation.
     */
    protected void checkpointComponent(File dir, String tagName)
            throws MojoExecutionException {
        CheckpointSupport.checkpoint(dir, tagName, getLog());
    }

    // ── Report ────────────────────────────────────────────────────────

    private String buildCheckpointMarkdownReport(
            List<ComponentSnapshot> snapshots, List<String> absent) {
        var sb = new StringBuilder();
        sb.append(snapshots.size()).append(" component(s) checkpointed");
        if (!absent.isEmpty()) {
            sb.append(", ").append(absent.size()).append(" absent");
        }
        sb.append(dryRun ? " (dry run)" : "").append(".\n\n");
        sb.append("| Component | Version | SHA | Branch | Status |\n");
        sb.append("|-----------|---------|-----|--------|--------|\n");
        for (var snap : snapshots) {
            sb.append("| ").append(snap.name())
                    .append(" | ").append(snap.version())
                    .append(" | ").append(snap.shortSha())
                    .append(" | ").append(snap.branch())
                    .append(" | ✓ |\n");
        }
        for (String name : absent) {
            sb.append("| ").append(name)
                    .append(" | — | — | — | not cloned |\n");
        }
        return sb.toString();
    }

    // ── YAML generation (pure, static, testable) ──────────────────────

    /**
     * Build checkpoint YAML content from pre-gathered component data.
     */
    public static String buildCheckpointYaml(String name, String timestamp,
                                              String author, String schemaVersion,
                                              List<ComponentSnapshot> snapshots,
                                              List<String> absentNames) {
        List<String> yaml = new ArrayList<>();
        yaml.add("# IKE Workspace Checkpoint");
        yaml.add("# Generated by: mvn ws:checkpoint-apply");
        yaml.add("#");
        yaml.add("checkpoint:");
        yaml.add("  name: \"" + name + "\"");
        yaml.add("  created: \"" + timestamp + "\"");
        yaml.add("  author: \"" + author + "\"");
        yaml.add("  schema-version: \"" + schemaVersion + "\"");
        yaml.add("");
        yaml.add("  components:");

        for (String absent : absentNames) {
            yaml.add("    " + absent + ":");
            yaml.add("      status: absent");
        }

        for (ComponentSnapshot snap : snapshots) {
            yaml.add("    " + snap.name() + ":");
            if (snap.version() != null) {
                yaml.add("      version: \"" + snap.version() + "\"");
            }
            yaml.add("      sha: \"" + snap.sha() + "\"");
            yaml.add("      short-sha: \"" + snap.shortSha() + "\"");
            yaml.add("      branch: \"" + snap.branch() + "\"");
            yaml.add("      type: " + snap.type());
            if (snap.compositeCheckpoint()) {
                yaml.add("      # TODO: add view-coordinate from Tinkar runtime");
            }
        }

        return String.join("\n", yaml) + "\n";
    }

    public static String checkpointFileName(String checkpointName) {
        return "checkpoint-" + checkpointName + ".yaml";
    }

    // ── Private helpers ────────────────────────────────────────────────

    private String gitFullSha(File dir) {
        try {
            return ReleaseSupport.execCapture(dir, "git", "rev-parse", "HEAD");
        } catch (MojoExecutionException e) {
            return "unknown";
        }
    }

    private String readVersion(File dir) throws MojoExecutionException {
        return ReleaseSupport.readPomVersion(new File(dir, "pom.xml"));
    }

    private String resolveAuthor(File root) {
        try {
            return ReleaseSupport.execCapture(root, "git", "config", "user.name");
        } catch (MojoExecutionException e) {
            return System.getProperty("user.name", "unknown");
        }
    }

    private String snapshotTestingContext(WorkspaceGraph graph)
            throws MojoExecutionException {
        if (issueRepo == null || issueRepo.isBlank()) return null;

        String milestoneName = milestone;

        if (milestoneName == null || milestoneName.isBlank()) {
            var components = graph.manifest().components();
            if (!components.isEmpty()) {
                var first = components.entrySet().iterator().next();
                String compName = first.getKey();
                String version = first.getValue().version();
                if (version != null) {
                    String releaseVersion = version.replace("-SNAPSHOT", "");
                    milestoneName = compName + " v" + releaseVersion;
                }
            }
        }

        if (milestoneName == null || milestoneName.isBlank()) return null;

        getLog().info("  Querying milestone: " + milestoneName);
        var context = ReleaseNotesSupport.snapshotMilestone(
                issueRepo, milestoneName, getLog());

        if (context == null) {
            getLog().info("  No milestone found — skipping testing context");
            return null;
        }

        getLog().info("  Testing context: "
                + context.readyToTest().size() + " ready, "
                + context.inProgress().size() + " in progress");

        return context.toYaml("  ");
    }

    private String deriveCheckpointName(File root) throws MojoExecutionException {
        String branch = gitBranch(root);
        String safeBranch = branch.replace('/', '-');
        String compactTime = COMPACT_UTC.format(Instant.now());
        return safeBranch + "-" + compactTime;
    }
}
