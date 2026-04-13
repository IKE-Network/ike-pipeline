package network.ike.plugin.ws;

import network.ike.plugin.ws.vcs.VcsOperations;
import network.ike.plugin.ws.vcs.VcsState;
import network.ike.workspace.WorkspaceGraph;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Push with a VCS bridge catch-up preamble.
 *
 * <p>When run from a workspace root (where {@code workspace.yaml} exists),
 * iterates all component repositories in topological order and pushes each.
 * When run from a single repository, operates on the current directory only.
 *
 * <p>Usage:
 * <pre>{@code
 * mvn ws:push
 * mvn ws:push -Dgroup=core
 * }</pre>
 */
@Mojo(name = "push", requiresProject = false, threadSafe = true)
public class PushMojo extends AbstractWorkspaceMojo {

    /** Creates this goal instance. */
    public PushMojo() {}

    /**
     * Remote name to push to.
     */
    @Parameter(property = "remote", defaultValue = "origin")
    String remote;

    /**
     * Restrict to a named group (or single component). Default: all.
     */
    @Parameter(property = "group")
    String group;

    @Override
    public void execute() throws MojoExecutionException {
        if (isWorkspaceMode()) {
            executeWorkspace();
        } else {
            executeSingleRepo(new File(System.getProperty("user.dir")));
        }
    }

    private void executeWorkspace() throws MojoExecutionException {
        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();

        Set<String> targets;
        if (group != null && !group.isEmpty()) {
            targets = graph.expandGroup(group);
        } else {
            targets = graph.manifest().components().keySet();
        }

        List<String> sorted = graph.topologicalSort(new LinkedHashSet<>(targets));

        getLog().info("");
        getLog().info(header("Push"));
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("");

        int pushed = 0;
        int skipped = 0;
        int failed = 0;

        for (String name : sorted) {
            File dir = new File(root, name);
            File gitDir = new File(dir, ".git");

            if (!gitDir.exists()) {
                getLog().debug(name + " — not cloned, skipping");
                skipped++;
                continue;
            }

            try {
                VcsOperations.catchUp(dir, getLog());

                String branch = VcsOperations.currentBranch(dir);
                VcsOperations.push(dir, getLog(), remote, branch);
                VcsOperations.writeVcsState(dir, VcsState.Action.PUSH);

                getLog().info(Ansi.green("  ✓ ") + name + " → " + remote + "/" + branch);
                pushed++;
            } catch (MojoExecutionException e) {
                getLog().warn(Ansi.red("  ✗ ") + name + " — " + e.getMessage());
                failed++;
            }
        }

        getLog().info("");
        getLog().info("  Done: " + pushed + " pushed, " + skipped
                + " skipped, " + failed + " failed");
        getLog().info("");

        if (failed > 0) {
            getLog().warn("  Some pushes failed — check output above for details.");
        }

        writeReport("ws:push", pushed + " pushed, " + skipped
                + " skipped, " + failed + " failed.\n");
    }

    private void executeSingleRepo(File dir) throws MojoExecutionException {
        getLog().info("");
        getLog().info("IKE VCS Bridge — Push");
        getLog().info("══════════════════════════════════════════════════════════════");

        VcsOperations.catchUp(dir, getLog());

        String branch = VcsOperations.currentBranch(dir);
        getLog().info("  Pushing to " + remote + "/" + branch + "...");
        VcsOperations.push(dir, getLog(), remote, branch);

        VcsOperations.writeVcsState(dir, VcsState.Action.PUSH);

        getLog().info("");
        getLog().info("  Done.");
        getLog().info("");
    }
}
