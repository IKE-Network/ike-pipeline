package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;
import network.ike.plugin.ws.preflight.Preflight;
import network.ike.plugin.ws.preflight.PreflightCondition;
import network.ike.plugin.ws.preflight.PreflightContext;

import network.ike.workspace.Component;
import network.ike.workspace.WorkspaceGraph;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pull latest changes across workspace components.
 *
 * <p>Runs {@code git pull --rebase} in each cloned component directory
 * in topological order (dependencies first). Uninitialized components
 * are skipped with a warning.
 *
 * <pre>{@code
 * mvn ike:pull
 * mvn ike:pull -Dgroup=studio
 * }</pre>
 */
@Mojo(name = "pull", projectRequired = false)
public class PullWorkspaceMojo extends AbstractWorkspaceMojo {

    /**
     * Restrict to a named group (or single component). Default: all.
     */
    @Parameter(property = "group")
    private String group;

    /** Creates this goal instance. */
    public PullWorkspaceMojo() {}

    @Override
    public void execute() throws MojoException {
        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();

        Set<String> targets;
        if (group != null && !group.isEmpty()) {
            targets = graph.expandGroup(group);
        } else {
            targets = graph.manifest().components().keySet();
        }

        List<String> sorted = graph.topologicalSort(new LinkedHashSet<>(targets));

        // Preflight: all working trees must be clean (#132, #154)
        Preflight.of(
                List.of(PreflightCondition.WORKING_TREE_CLEAN),
                PreflightContext.of(root, graph, sorted))
                .requirePassed(WsGoal.PULL);

        getLog().info("");
        getLog().info(header("Pull"));
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("");

        int pulled = 0;
        int skipped = 0;
        int failed = 0;

        for (String name : sorted) {
            File dir = new File(root, name);
            File gitDir = new File(dir, ".git");

            if (!gitDir.exists()) {
                getLog().info(Ansi.yellow("  ⚠ ") + name + " — not cloned, skipping");
                skipped++;
                continue;
            }

            getLog().info(Ansi.cyan("  ↓ ") + name);
            try {
                ReleaseSupport.exec(dir, getLog(),
                        "git", "pull", "--rebase");
                pulled++;
            } catch (MojoException e) {
                getLog().warn(Ansi.red("  ✗ ") + name + " — pull failed: " + e.getMessage());
                failed++;
            }
        }

        getLog().info("");
        getLog().info("  Done: " + pulled + " pulled, " + skipped
                + " skipped, " + failed + " failed");
        getLog().info("");

        if (failed > 0) {
            getLog().warn("  Some pulls failed — check output above for details.");
        }

        // Structured markdown report
        writeReport(WsGoal.PULL, pulled + " pulled, " + skipped
                + " skipped, " + failed + " failed.\n");
    }
}
