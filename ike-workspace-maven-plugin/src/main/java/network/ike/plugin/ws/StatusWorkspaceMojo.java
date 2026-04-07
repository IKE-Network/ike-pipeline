package network.ike.plugin.ws;

import network.ike.workspace.Component;
import network.ike.workspace.WorkspaceGraph;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Show git status across all workspace components.
 *
 * <p>For each component directory that exists, reports the current
 * branch, short SHA, and whether the working tree is clean or dirty.
 * Missing directories are flagged as "not cloned".
 *
 * <pre>{@code
 * mvn ike:status
 * mvn ike:status -Dgroup=studio
 * }</pre>
 */
@Mojo(name = "status", requiresProject = false, threadSafe = true)
public class StatusWorkspaceMojo extends AbstractWorkspaceMojo {

    /**
     * Restrict to a named group (or single component). Default: all.
     */
    @Parameter(property = "group")
    String group;

    /** Creates this goal instance. */
    public StatusWorkspaceMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        ReportLog report = startReport();
        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();

        getLog().info("");
        getLog().info(header("Status"));
        getLog().info("══════════════════════════════════════════════════════════════");

        Set<String> targets;
        if (group != null && !group.isEmpty()) {
            targets = graph.expandGroup(group);
            getLog().info("  Group: " + group + " → " + targets.size() + " components");
        } else {
            targets = graph.manifest().components().keySet();
        }

        getLog().info("");
        getLog().info(String.format("  %-28s %-28s %-10s %s",
                "COMPONENT", "BRANCH", "SHA", "STATUS"));
        getLog().info(String.format("  %-28s %-28s %-10s %s",
                "─────────", "──────", "───", "──────"));

        int cloned = 0;
        int modified = 0;
        List<String[]> rows = new ArrayList<>();

        for (String name : targets) {
            Component component = graph.manifest().components().get(name);
            File dir = new File(root, name);

            if (!dir.exists()) {
                getLog().info(String.format("  %-28s %-28s %-10s %s",
                        name, "—", "—", "not cloned"));
                rows.add(new String[]{name, "—", "—", "not cloned"});
                continue;
            }

            cloned++;
            String branch = gitBranch(dir);
            String sha = gitShortSha(dir);
            String status = gitStatus(dir);

            String statusLabel;
            if (status.isEmpty()) {
                statusLabel = "clean";
            } else {
                modified++;
                long changed = status.lines().count();
                statusLabel = "modified (" + changed + " file"
                        + (changed == 1 ? "" : "s") + ")";
            }

            // Flag branch mismatch with manifest
            String branchDisplay = branch;
            if (component.branch() != null
                    && !branch.equals(component.branch())) {
                branchDisplay = branch + " (expected: " + component.branch() + ")";
            }

            getLog().info(String.format("  %-28s %-28s %-10s %s",
                    name, branchDisplay, sha, statusLabel));
            rows.add(new String[]{name, branchDisplay, sha, statusLabel});
        }

        getLog().info("");
        getLog().info("  " + cloned + "/" + targets.size() + " cloned, "
                + modified + " modified");
        getLog().info("");
        finishReport("ws:status", report);

        // Structured markdown report
        appendReport("ws:status", buildMarkdownReport(
                rows, cloned, targets.size(), modified));
    }

    private String buildMarkdownReport(List<String[]> rows,
                                        int cloned, int total, int modified) {
        var sb = new StringBuilder();
        sb.append(cloned).append('/').append(total)
                .append(" cloned, ").append(modified).append(" modified.\n\n");
        sb.append("| Component | Branch | SHA | Status |\n");
        sb.append("|-----------|--------|-----|--------|\n");
        for (String[] row : rows) {
            sb.append("| ").append(row[0])
                    .append(" | ").append(row[1])
                    .append(" | ").append(row[2])
                    .append(" | ").append(row[3])
                    .append(" |\n");
        }
        return sb.toString();
    }
}
