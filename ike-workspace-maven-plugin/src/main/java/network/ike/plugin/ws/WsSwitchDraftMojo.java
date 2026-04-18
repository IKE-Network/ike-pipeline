package network.ike.plugin.ws;

import network.ike.workspace.ManifestWriter;
import network.ike.workspace.WorkspaceGraph;
import network.ike.plugin.ws.preflight.Preflight;
import network.ike.plugin.ws.preflight.PreflightCondition;
import network.ike.plugin.ws.preflight.PreflightContext;
import network.ike.plugin.ws.preflight.PreflightResult;
import network.ike.plugin.ws.vcs.VcsOperations;
import network.ike.plugin.ws.vcs.VcsState;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Switch all workspace subprojects to a different branch.
 *
 * <p>Discovers all local feature branches across components and presents
 * an interactive menu. The selected branch is checked out in every
 * subproject that has it locally; subprojects without the branch are
 * skipped with a warning.
 *
 * <p>Before switching, validates that all subproject working trees are
 * clean. If any have uncommitted changes, the goal fails with a list
 * of the affected subprojects and a "commit or stash, then try again"
 * message.
 *
 * <p>After switching, updates workspace.yaml branch fields and commits
 * the change.
 *
 * <pre>{@code
 * mvn ws:switch                        # interactive menu
 * mvn ws:switch -Dbranch=feature/foo   # non-interactive
 * mvn ws:switch -Dbranch=main          # switch all to main
 * }</pre>
 *
 * @see FeatureStartDraftMojo for creating feature branches
 */
@Mojo(name = "switch-draft", projectRequired = false)
public class WsSwitchDraftMojo extends AbstractWorkspaceMojo {

    /** Creates this goal instance. */
    public WsSwitchDraftMojo() {}

    /**
     * Target branch to switch to. If omitted, presents an interactive
     * menu of available branches.
     */
    @Parameter(property = "branch")
    String branch;

    /** Execute the switch. Default is draft (preview only). */
    @Parameter(property = "publish", defaultValue = "false")
    boolean publish;

    @Override
    public void execute() throws MojoException {
        if (!isWorkspaceMode()) {
            throw new MojoException(
                    "ws:switch requires a workspace (workspace.yaml). "
                    + "Use 'git checkout <branch>' for single-repo switching.");
        }

        boolean draft = !publish;
        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();

        Set<String> targets = graph.manifest().subprojects().keySet();

        List<String> sorted = graph.topologicalSort(new LinkedHashSet<>(targets));

        // ── Discover branches ────────────────────────────────────
        // Map: branch name → set of components that have it locally
        Map<String, Set<String>> branchComponents = new TreeMap<>();
        branchComponents.put("main", new TreeSet<>());

        String currentBranch = null;
        Map<String, Integer> branchCounts = new TreeMap<>();

        for (String name : sorted) {
            File dir = new File(root, name);
            if (!new File(dir, ".git").exists()) continue;

            String compBranch = gitBranch(dir);
            branchCounts.merge(compBranch, 1, Integer::sum);

            // Add main for every cloned subproject
            branchComponents.get("main").add(name);

            // Discover all local feature branches
            List<String> localFeatures = VcsOperations.localBranches(dir, "feature/");
            for (String fb : localFeatures) {
                branchComponents.computeIfAbsent(fb, _ -> new TreeSet<>()).add(name);
            }
        }

        // Determine current branch (majority vote)
        currentBranch = branchCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("main");

        // ── Resolve target branch ────────────────────────────────
        if (branch == null || branch.isBlank()) {
            branch = promptForBranch(branchComponents, currentBranch);
        }

        if (branch.equals(currentBranch)) {
            getLog().info("Already on " + currentBranch + " — nothing to do.");
            return;
        }

        // Validate the target branch exists somewhere (or is main)
        if (!branch.equals("main") && !branchComponents.containsKey(branch)) {
            throw new MojoException(
                    "Branch '" + branch + "' does not exist in any subproject. "
                    + "Available: " + branchComponents.keySet());
        }

        getLog().info("");
        getLog().info(header("Switch"));
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("  From:  " + currentBranch);
        getLog().info("  To:    " + branch);
        if (draft) getLog().info("  Mode:  DRAFT");
        getLog().info("");

        // ── Validate clean working trees (#154) ──────────────────
        PreflightResult switchPreflight = Preflight.of(
                List.of(PreflightCondition.WORKING_TREE_CLEAN),
                PreflightContext.of(root, graph, sorted));
        if (draft) {
            switchPreflight.warnIfFailed(getLog(), WsGoal.SWITCH_PUBLISH);
        } else {
            switchPreflight.requirePassed(WsGoal.SWITCH_PUBLISH);
        }

        // ── Switch components ────────────────────────────────────
        int switched = 0;
        int skipped = 0;
        for (String name : sorted) {
            File dir = new File(root, name);
            if (!new File(dir, ".git").exists()) {
                skipped++;
                continue;
            }

            String compBranch = gitBranch(dir);
            if (compBranch.equals(branch)) {
                getLog().info("  " + Ansi.green("✓ ") + name + " — already on " + branch);
                switched++;
                continue;
            }

            // Check if the target branch exists locally in this subproject
            if (!branch.equals("main")) {
                List<String> localBranches = VcsOperations.localBranches(dir, "");
                if (!localBranches.contains(branch)) {
                    getLog().info("  " + Ansi.yellow("⚠ ") + name
                            + " — branch " + branch + " does not exist locally, skipping");
                    skipped++;
                    continue;
                }
            }

            if (draft) {
                getLog().info("  [draft] " + name + " — would switch "
                        + compBranch + " → " + branch);
                switched++;
                continue;
            }

            getLog().info("  " + Ansi.cyan("→ ") + name + ": " + compBranch + " → " + branch);
            VcsOperations.checkout(dir, getLog(), branch);
            switched++;
        }

        // ── Update workspace.yaml ────────────────────────────────
        if (!draft && switched > 0) {
            updateWorkspaceYaml(sorted, branch);
            switchWorkspaceRepo(branch);
        }

        getLog().info("");
        getLog().info("  Switched: " + switched
                + " | Skipped: " + skipped);
        getLog().info("");

        // Write report
        var sb = new StringBuilder();
        sb.append("**From:** `").append(currentBranch)
          .append("` **To:** `").append(branch).append("`\n\n");
        sb.append("**").append(switched).append("** switched, **")
          .append(skipped).append("** skipped.\n");
        writeReport(publish ? WsGoal.SWITCH_PUBLISH : WsGoal.SWITCH_DRAFT,
                sb.toString());
    }

    /**
     * Present an interactive menu of available branches and prompt for selection.
     *
     * @param branchComponents map of branch name to components that have it
     * @param currentBranch    the current majority branch
     * @return the selected branch name
     * @throws MojoException if no console or invalid selection
     */
    private String promptForBranch(Map<String, Set<String>> branchComponents,
                                    String currentBranch)
            throws MojoException {
        java.io.Console console = System.console();
        if (console == null) {
            throw new MojoException(
                    "No interactive console available. Use -Dbranch=<name> to specify target.");
        }

        List<String> branches = new ArrayList<>(branchComponents.keySet());

        getLog().info("");
        getLog().info(header("Switch"));
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("");
        getLog().info("  Available branches:");
        getLog().info("");
        for (int i = 0; i < branches.size(); i++) {
            String b = branches.get(i);
            int count = branchComponents.get(b).size();
            String current = b.equals(currentBranch) ? " (current)" : "";
            getLog().info("    " + (i + 1) + ". " + b
                    + " (" + count + " subproject" + (count == 1 ? "" : "s") + ")"
                    + current);
        }
        getLog().info("");

        String input = console.readLine("  Select branch [1-" + branches.size() + "]: ");
        if (input == null || input.isBlank()) {
            throw new MojoException("No selection made.");
        }

        try {
            int idx = Integer.parseInt(input.trim()) - 1;
            if (idx < 0 || idx >= branches.size()) {
                throw new MojoException(
                        "Invalid selection: " + input + ". Expected 1-" + branches.size());
            }
            return branches.get(idx);
        } catch (NumberFormatException e) {
            // Allow typing the branch name directly
            String typed = input.trim();
            if (branchComponents.containsKey(typed) || "main".equals(typed)) {
                return typed;
            }
            throw new MojoException(
                    "Unknown branch: " + typed + ". Available: " + branchComponents.keySet());
        }
    }

    /**
     * Update workspace.yaml branch fields and commit the change.
     */
    private void updateWorkspaceYaml(List<String> components, String targetBranch)
            throws MojoException {
        try {
            Path manifestPath = resolveManifest();
            Map<String, String> updates = new LinkedHashMap<>();
            for (String name : components) {
                updates.put(name, targetBranch);
            }
            ManifestWriter.updateBranches(manifestPath, updates);
            getLog().info("  Updated workspace.yaml branches → " + targetBranch);
        } catch (IOException e) {
            getLog().warn("  Could not update workspace.yaml: " + e.getMessage());
        }
    }

    /**
     * Switch the workspace repo itself to the target branch and commit
     * the workspace.yaml update.
     */
    private void switchWorkspaceRepo(String targetBranch) throws MojoException {
        try {
            Path manifestPath = resolveManifest();
            File wsRoot = manifestPath.getParent().toFile();
            if (!new File(wsRoot, ".git").exists()) return;

            String wsBranch = VcsOperations.currentBranch(wsRoot);
            if (!wsBranch.equals(targetBranch)) {
                // Check if target branch exists locally in workspace repo
                List<String> localBranches = VcsOperations.localBranches(wsRoot, "");
                if (localBranches.contains(targetBranch) || "main".equals(targetBranch)) {
                    getLog().info("  Workspace repo: " + wsBranch + " → " + targetBranch);
                    VcsOperations.checkout(wsRoot, getLog(), targetBranch);
                }
            }

            // Stage and commit workspace.yaml if changed
            network.ike.plugin.ReleaseSupport.exec(
                    wsRoot, getLog(), "git", "add", "workspace.yaml");
            if (VcsOperations.hasStagedChanges(wsRoot)) {
                VcsOperations.commit(wsRoot, getLog(),
                        "workspace: switch branches to " + targetBranch);
            }

            VcsOperations.writeVcsState(wsRoot, VcsState.Action.SWITCH);
        } catch (MojoException e) {
            getLog().warn("  Could not switch workspace repo: " + e.getMessage());
        }
    }
}
