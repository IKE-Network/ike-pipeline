package network.ike.plugin.ws;

import network.ike.workspace.Component;
import network.ike.workspace.WorkspaceGraph;
import network.ike.plugin.ws.vcs.VcsOperations;
import network.ike.plugin.ws.vcs.VcsState;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Rebase feature branch onto target, then fast-forward merge.
 *
 * <p>Replays each feature commit individually onto the target branch,
 * producing <b>linear history</b> without a merge commit. The feature
 * branch is kept by default but can be deleted.
 *
 * <p><b>Caution:</b> rebasing rewrites the feature branch history.
 * Other machines must run {@code ike:sync} after the rebase to pick up
 * the rewritten history.
 *
 * <p>When to use: small features where each commit is meaningful and
 * you want them replayed individually on the target branch without
 * a merge commit.
 *
 * <pre>{@code
 * mvn ike:feature-finish-rebase -Dfeature=small-fix
 * mvn ike:feature-finish-rebase -Dfeature=cleanup -DkeepBranch=false
 * }</pre>
 *
 * @see FeatureFinishSquashMojo for clean single-commit merges (default)
 * @see FeatureFinishMergeMojo for preserving full history
 */
@Mojo(name = "feature-finish-rebase", requiresProject = false, threadSafe = true)
public class FeatureFinishRebaseMojo extends AbstractWorkspaceMojo {

    /** Creates this goal instance. */
    public FeatureFinishRebaseMojo() {}

    @Parameter(property = "feature")
    String feature;

    @Parameter(property = "group")
    String group;

    @Parameter(property = "targetBranch", defaultValue = "main")
    String targetBranch;

    /**
     * Keep the feature branch after rebase. Default is true.
     * Note: the branch has been rebased — its history is rewritten.
     */
    @Parameter(property = "keepBranch", defaultValue = "true")
    boolean keepBranch = true;

    @Parameter(property = "dryRun", defaultValue = "true")
    boolean dryRun;

    @Override
    public void execute() throws MojoExecutionException {
        feature = requireParam(feature, "feature",
                "Feature to rebase (without feature/ prefix)");
        String branchName = "feature/" + feature;

        if (!isWorkspaceMode()) {
            executeBareMode(branchName);
            return;
        }

        executeWorkspaceMode(branchName);
    }

    private void executeWorkspaceMode(String branchName) throws MojoExecutionException {
        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();
        Path manifestPath = resolveManifest();

        var targets = group != null && !group.isEmpty()
                ? graph.expandGroup(group)
                : graph.manifest().components().keySet();
        List<String> sorted = graph.topologicalSort(new LinkedHashSet<>(targets));
        List<String> reversed = new ArrayList<>(sorted);
        Collections.reverse(reversed);

        getLog().info("");
        getLog().info(header("Feature Finish (rebase)"));
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("  Feature:  " + feature);
        getLog().info("  Branch:   " + branchName + " → " + targetBranch);
        getLog().info("  Strategy: rebase + fast-forward");
        if (dryRun) getLog().info("  Mode:     DRY RUN");
        getLog().info("");

        VcsOperations.catchUp(root, getLog());

        List<String> eligible = new ArrayList<>();
        for (String name : reversed) {
            String reason = FeatureFinishSupport.validateComponent(
                    root, name, branchName, this);
            if (reason == null) {
                eligible.add(name);
            } else if ("DIRTY".equals(reason)) {
                throw new MojoExecutionException(
                        name + " has uncommitted changes. Commit or stash first.");
            } else {
                getLog().info(Ansi.yellow("  · ") + name + " — " + reason + ", skipping");
            }
        }

        if (eligible.isEmpty()) {
            getLog().info("  No components on " + branchName + " — nothing to do.");
            return;
        }

        int rebased = 0;
        for (String name : eligible) {
            Component component = graph.manifest().components().get(name);
            File dir = new File(root, name);

            if (dryRun) {
                getLog().info("  [dry-run] " + name + " — would rebase → " + targetBranch);
                rebased++;
                continue;
            }

            getLog().info(Ansi.cyan("  → ") + name);
            VcsOperations.catchUp(dir, getLog());
            FeatureFinishSupport.stripBranchVersion(dir, component, getLog());

            // Rebase feature onto target, then ff-merge
            VcsOperations.rebase(dir, getLog(), targetBranch);
            VcsOperations.checkout(dir, getLog(), targetBranch);
            VcsOperations.mergeFfOnly(dir, getLog(), branchName);
            VcsOperations.pushIfRemoteExists(dir, getLog(), "origin", targetBranch);

            if (!keepBranch) {
                FeatureFinishSupport.deleteBranch(dir, getLog(), branchName);
            }

            VcsOperations.writeVcsState(dir, VcsState.ACTION_FEATURE_FINISH);
            rebased++;
        }

        if (rebased > 0 && !dryRun) {
            FeatureFinishSupport.updateWorkspaceYaml(
                    manifestPath, eligible, targetBranch, feature, getLog());
            FeatureFinishSupport.mergeWorkspaceRepo(
                    manifestPath, branchName, targetBranch, keepBranch, getLog());
        }

        getLog().info("");
        getLog().info("  Rebased: " + rebased + " components");
        getLog().info("  Branch " + (keepBranch ? "kept" : "deleted") + ": " + branchName);
        getLog().info("");

        // Structured markdown report
        appendReport("ws:feature-finish-rebase", buildRebaseReport(
                eligible, branchName, targetBranch, rebased, dryRun, keepBranch));
    }

    private String buildRebaseReport(List<String> components, String branch,
                                      String target, int rebased,
                                      boolean isDryRun, boolean kept) {
        var sb = new StringBuilder();
        sb.append("**Branch:** `").append(branch).append("` → `")
          .append(target).append("`\n");
        sb.append("**Strategy:** rebase + fast-forward\n\n");

        sb.append("| Component | Status |\n");
        sb.append("|-----------|--------|\n");
        for (String name : components) {
            sb.append("| ").append(name).append(" | ")
              .append(isDryRun ? "would rebase" : "rebased").append(" |\n");
        }

        sb.append("\n**").append(rebased).append(" component(s)** ")
          .append(isDryRun ? "would be rebased" : "rebased")
          .append(". Branch ").append(kept ? "kept" : "deleted").append(".\n");
        return sb.toString();
    }

    private void executeBareMode(String branchName) throws MojoExecutionException {
        File dir = new File(System.getProperty("user.dir"));

        getLog().info("");
        getLog().info("IKE Feature Finish — Rebase (bare repo)");
        getLog().info("══════════════════════════════════════════════════════════════");

        VcsOperations.catchUp(dir, getLog());

        String currentBranch = gitBranch(dir);
        if (!currentBranch.equals(branchName)) {
            throw new MojoExecutionException(
                    "Not on " + branchName + " (currently on " + currentBranch + ")");
        }
        if (!gitStatus(dir).isEmpty()) {
            throw new MojoExecutionException("Uncommitted changes. Commit or stash first.");
        }

        if (dryRun) {
            getLog().info("  [dry-run] Would rebase → " + targetBranch);
            return;
        }

        FeatureFinishSupport.stripBranchVersionBare(dir, getLog());

        VcsOperations.rebase(dir, getLog(), targetBranch);
        VcsOperations.checkout(dir, getLog(), targetBranch);
        VcsOperations.mergeFfOnly(dir, getLog(), branchName);
        VcsOperations.pushIfRemoteExists(dir, getLog(), "origin", targetBranch);

        if (!keepBranch) {
            FeatureFinishSupport.deleteBranch(dir, getLog(), branchName);
        }

        VcsOperations.writeVcsState(dir, VcsState.ACTION_FEATURE_FINISH);

        getLog().info("  Done.");
        getLog().info("");
    }
}
