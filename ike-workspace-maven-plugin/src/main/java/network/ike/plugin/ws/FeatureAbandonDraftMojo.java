package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;

import network.ike.workspace.Component;
import network.ike.workspace.ManifestWriter;
import network.ike.workspace.WorkspaceGraph;
import network.ike.plugin.ws.vcs.VcsOperations;
import network.ike.plugin.ws.vcs.VcsState;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Abandon a feature branch across all workspace components.
 *
 * <p>Auto-detects the current feature branch from workspace components.
 * Shows a preview of what will be abandoned, then prompts for
 * confirmation before executing. No separate {@code -apply} variant
 * is needed — the goal handles preview and confirmation itself.
 *
 * <p>Components are processed in reverse topological order (downstream
 * first) to avoid transient dependency issues.
 *
 * <p>Safety: warns about unmerged commits before deleting. Use
 * {@code -Dforce=true} to suppress the warning and force-delete.
 *
 * <pre>{@code
 * mvn ws:feature-abandon                         # auto-detect, confirm
 * mvn ws:feature-abandon -Dfeature=my-experiment # explicit name
 * mvn ws:feature-abandon -DdeleteRemote=true     # also delete remote branches
 * mvn ws:feature-abandon -Dforce=true            # skip unmerged commit warning
 * }</pre>
 *
 * @see FeatureStartDraftMojo for creating feature branches
 */
@Mojo(name = "feature-abandon-draft", requiresProject = false, threadSafe = true)
public class FeatureAbandonDraftMojo extends AbstractWorkspaceMojo {

    @Parameter(property = "feature")
    String feature;

    @Parameter(property = "group")
    String group;

    @Parameter(property = "targetBranch")
    String targetBranch;

    @Parameter(property = "deleteRemote", defaultValue = "false")
    boolean deleteRemote;

    @Parameter(property = "force", defaultValue = "false")
    boolean force;

    /** Creates this goal instance. */
    public FeatureAbandonDraftMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        if (!isWorkspaceMode()) {
            executeBareMode();
            return;
        }

        executeWorkspaceMode();
    }

    private void executeWorkspaceMode() throws MojoExecutionException {
        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();
        Path manifestPath = resolveManifest();

        if (targetBranch == null || targetBranch.isBlank()) {
            targetBranch = graph.manifest().defaults().branch();
            if (targetBranch == null) targetBranch = "main";
        }

        Set<String> targets;
        if (group != null && !group.isEmpty()) {
            targets = graph.expandGroup(group);
        } else {
            targets = graph.manifest().components().keySet();
        }

        List<String> sorted = graph.topologicalSort(new LinkedHashSet<>(targets));
        List<String> reversed = new ArrayList<>(sorted);
        Collections.reverse(reversed);

        // Auto-detect feature branch if not specified
        if (feature == null || feature.isBlank()) {
            feature = detectFeatureBranch(root, reversed);
        }
        String branchName = "feature/" + feature;

        // Collect eligible components and show preview
        getLog().info("");
        getLog().info(header("Feature Abandon"));
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("  Feature:  " + feature);
        getLog().info("  Branch:   " + branchName + " → " + targetBranch);
        if (deleteRemote) getLog().info("  Remote:   will delete origin/" + branchName);
        getLog().info("");

        List<String> eligible = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (String name : reversed) {
            File dir = new File(root, name);
            File gitDir = new File(dir, ".git");

            if (!gitDir.exists()) {
                getLog().info(Ansi.yellow("  · ") + name + " — not cloned");
                skipped.add(name);
                continue;
            }

            String currentBranch = gitBranch(dir);
            if (!currentBranch.equals(branchName)) {
                getLog().info(Ansi.yellow("  · ") + name + " — on "
                        + currentBranch + ", not on feature");
                skipped.add(name);
                continue;
            }

            // Check for uncommitted changes
            String status = gitStatus(dir);
            if (!status.isEmpty()) {
                throw new MojoExecutionException(
                        name + " has uncommitted changes. Commit, stash, or discard before abandoning.");
            }

            // Check for unmerged commits
            int unmergedCount = 0;
            try {
                String unmerged = ReleaseSupport.execCapture(dir,
                        "git", "log", "--oneline",
                        targetBranch + ".." + branchName);
                if (!unmerged.isBlank()) {
                    unmergedCount = (int) unmerged.lines().count();
                }
            } catch (MojoExecutionException e) {
                // Target branch may not exist locally
            }

            if (unmergedCount > 0) {
                getLog().info(Ansi.yellow("  ⚠ ") + name + " — "
                        + unmergedCount + " unmerged commit(s)");
            } else {
                getLog().info(Ansi.cyan("  → ") + name + " — on " + branchName);
            }
            eligible.add(name);
        }

        if (eligible.isEmpty()) {
            getLog().info("  No components on " + branchName + " — nothing to abandon.");
            getLog().info("");
            return;
        }

        // Prompt for confirmation
        getLog().info("");
        getLog().info("  " + eligible.size() + " component(s) will switch to "
                + targetBranch + " and delete " + branchName);
        if (!force) {
            java.io.Console console = System.console();
            if (console != null) {
                String response = console.readLine(
                        Ansi.YELLOW + "  Abandon feature/%s? (yes/no): " + Ansi.RESET,
                        feature);
                if (response == null || !response.trim().toLowerCase().startsWith("y")) {
                    throw new MojoExecutionException("Abandon cancelled.");
                }
            }
        }

        // Execute
        for (String name : eligible) {
            Component component = graph.manifest().components().get(name);
            File dir = new File(root, name);

            // Strip branch-qualified versions before switching
            FeatureFinishSupport.stripBranchVersion(dir, component, getLog());

            VcsOperations.checkout(dir, getLog(), targetBranch);
            VcsOperations.deleteBranch(dir, getLog(), branchName);

            if (deleteRemote) {
                try {
                    VcsOperations.deleteRemoteBranch(dir, getLog(), "origin", branchName);
                } catch (MojoExecutionException e) {
                    getLog().warn("    could not delete remote branch: " + e.getMessage());
                }
            }

            VcsOperations.writeVcsState(dir, VcsState.Action.FEATURE_FINISH);
            getLog().info(Ansi.green("  ✓ ") + name + " → " + targetBranch);
        }

        // Update workspace.yaml and workspace repo
        if (!eligible.isEmpty()) {
            abandonWorkspaceRepo(manifestPath, eligible, branchName);
        }

        getLog().info("");
        getLog().info("  Abandoned: " + eligible.size()
                + " | Skipped: " + skipped.size());
        if (!deleteRemote) {
            getLog().info("  Remote branches kept. Use -DdeleteRemote=true to delete them.");
        }
        getLog().info("");

        // Structured markdown report
        var sb = new StringBuilder();
        sb.append("**Branch:** `").append(branchName).append("`\n\n");
        sb.append("| Component | Status |\n");
        sb.append("|-----------|--------|\n");
        for (String name : eligible) {
            sb.append("| ").append(name).append(" | abandoned |\n");
        }
        for (String name : skipped) {
            sb.append("| ").append(name).append(" | skipped |\n");
        }
        sb.append("\n**").append(eligible.size()).append("** abandoned, **")
          .append(skipped.size()).append("** skipped.\n");
        appendReport("ws:feature-abandon", sb.toString());
    }

    // ── Auto-detect ─────────────────────────────────────────────────

    /**
     * Scan workspace components for feature branches and return
     * the feature name. If multiple features are found, prompts
     * the user to choose.
     */
    private String detectFeatureBranch(File root, List<String> components)
            throws MojoExecutionException {
        Set<String> features = new TreeSet<>();

        for (String name : components) {
            File dir = new File(root, name);
            if (!new File(dir, ".git").exists()) continue;

            String branch = gitBranch(dir);
            if (branch.startsWith("feature/")) {
                features.add(branch.substring("feature/".length()));
            }
        }

        if (features.isEmpty()) {
            throw new MojoExecutionException(
                    "No components are on a feature branch. Nothing to abandon.");
        }

        if (features.size() == 1) {
            String detected = features.iterator().next();
            getLog().info("  Detected feature: " + detected);
            return detected;
        }

        // Multiple features — list them and prompt
        getLog().info("  Multiple feature branches detected:");
        int i = 1;
        List<String> featureList = new ArrayList<>(features);
        for (String f : featureList) {
            getLog().info("    " + i + ". " + f);
            i++;
        }

        java.io.Console console = System.console();
        if (console != null) {
            String response = console.readLine(
                    Ansi.YELLOW + "  Feature to abandon (name or number): " + Ansi.RESET);
            if (response != null && !response.isBlank()) {
                String trimmed = response.trim();
                // Try as number first
                try {
                    int idx = Integer.parseInt(trimmed) - 1;
                    if (idx >= 0 && idx < featureList.size()) {
                        return featureList.get(idx);
                    }
                } catch (NumberFormatException _) {
                    // Not a number — treat as name
                }
                return trimmed;
            }
        }

        throw new MojoExecutionException(
                "Multiple features found: " + features
                        + ". Specify with -Dfeature=<name>.");
    }

    // ── Bare mode ───────────────────────────────────────────────────

    private void executeBareMode() throws MojoExecutionException {
        File dir = new File(System.getProperty("user.dir"));

        if (targetBranch == null || targetBranch.isBlank()) {
            targetBranch = "main";
        }

        String currentBranch = gitBranch(dir);
        if (feature == null || feature.isBlank()) {
            if (currentBranch.startsWith("feature/")) {
                feature = currentBranch.substring("feature/".length());
            } else {
                throw new MojoExecutionException(
                        "Not on a feature branch (on " + currentBranch
                                + "). Specify with -Dfeature=<name>.");
            }
        }
        String branchName = "feature/" + feature;

        getLog().info("");
        getLog().info("IKE Feature Abandon (bare repo)");
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("  Feature: " + feature);
        getLog().info("  Branch:  " + branchName + " → " + targetBranch);
        getLog().info("");

        if (!currentBranch.equals(branchName)) {
            throw new MojoExecutionException(
                    "Not on " + branchName + " (currently on " + currentBranch + ")");
        }
        if (!gitStatus(dir).isEmpty()) {
            throw new MojoExecutionException(
                    "Uncommitted changes. Commit, stash, or discard first.");
        }

        FeatureFinishSupport.stripBranchVersionBare(dir, getLog());

        VcsOperations.checkout(dir, getLog(), targetBranch);
        VcsOperations.deleteBranch(dir, getLog(), branchName);
        getLog().info(Ansi.green("  ✓ ") + "Switched to " + targetBranch
                + ", deleted " + branchName);

        if (deleteRemote) {
            try {
                VcsOperations.deleteRemoteBranch(dir, getLog(), "origin", branchName);
                getLog().info(Ansi.green("  ✓ ") + "Deleted remote branch");
            } catch (MojoExecutionException e) {
                getLog().warn("  Could not delete remote branch: " + e.getMessage());
            }
        }

        VcsOperations.writeVcsState(dir, VcsState.Action.FEATURE_FINISH);
        getLog().info("");
    }

    // ── Workspace repo cleanup ──────────────────────────────────────

    private void abandonWorkspaceRepo(Path manifestPath,
                                       List<String> components,
                                       String branchName)
            throws MojoExecutionException {
        try {
            Map<String, String> updates = new LinkedHashMap<>();
            for (String name : components) {
                updates.put(name, targetBranch);
            }
            ManifestWriter.updateBranches(manifestPath, updates);

            File wsRoot = manifestPath.getParent().toFile();
            if (!new File(wsRoot, ".git").exists()) return;

            String wsBranch = gitBranch(wsRoot);
            if (wsBranch.equals(branchName)) {
                ReleaseSupport.exec(wsRoot, getLog(), "git", "add", "workspace.yaml");
                if (VcsOperations.hasStagedChanges(wsRoot)) {
                    VcsOperations.commit(wsRoot, getLog(),
                            "workspace: revert branches for abandon " + branchName);
                }

                VcsOperations.checkout(wsRoot, getLog(), targetBranch);

                try {
                    ReleaseSupport.exec(wsRoot, getLog(),
                            "git", "cherry-pick", branchName);
                } catch (MojoExecutionException e) {
                    ManifestWriter.updateBranches(manifestPath, updates);
                    ReleaseSupport.exec(wsRoot, getLog(), "git", "add", "workspace.yaml");
                    if (VcsOperations.hasStagedChanges(wsRoot)) {
                        VcsOperations.commit(wsRoot, getLog(),
                                "workspace: revert branches after abandon " + branchName);
                    }
                }

                VcsOperations.deleteBranch(wsRoot, getLog(), branchName);

                if (deleteRemote) {
                    try {
                        VcsOperations.deleteRemoteBranch(wsRoot, getLog(), "origin", branchName);
                    } catch (MojoExecutionException e) {
                        getLog().warn("  Could not delete workspace remote branch: "
                                + e.getMessage());
                    }
                }
            } else {
                ReleaseSupport.exec(wsRoot, getLog(), "git", "add", "workspace.yaml");
                if (VcsOperations.hasStagedChanges(wsRoot)) {
                    VcsOperations.commit(wsRoot, getLog(),
                            "workspace: revert branches after abandon " + branchName);
                }
            }

            VcsOperations.pushIfRemoteExists(wsRoot, getLog(), "origin", targetBranch);

        } catch (IOException e) {
            getLog().warn("  Could not update workspace.yaml: " + e.getMessage());
        }
    }
}
