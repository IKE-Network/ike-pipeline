package network.ike.plugin.ws;

import network.ike.workspace.Component;
import network.ike.workspace.Dependency;
import network.ike.workspace.WorkspaceGraph;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Composite workspace dashboard — one invocation, full overview.
 *
 * <p>Runs verify + status + cascade-from-dirty in a single pass,
 * loading the manifest once and iterating components once. This is
 * the recommended "morning standup" command.
 *
 * <pre>{@code mvn ike:dashboard}</pre>
 */
@Mojo(name = "dashboard", requiresProject = false, threadSafe = true)
public class DashboardWorkspaceMojo extends AbstractWorkspaceMojo {

    /** Creates this goal instance. */
    public DashboardWorkspaceMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();

        getLog().info("");
        getLog().info(header("Dashboard"));
        getLog().info("══════════════════════════════════════════════════════════════");

        // ── Section 1: Manifest health ──────────────────────────────
        List<String> errors = graph.verify();
        getLog().info("");
        if (errors.isEmpty()) {
            getLog().info(Ansi.green("  ✓ ") + "Manifest: " + graph.manifest().components().size()
                    + " components, " + graph.manifest().groups().size()
                    + " groups — consistent");
        } else {
            getLog().warn(Ansi.red("  ✗ ") + "Manifest: " + errors.size() + " error(s)");
            for (String error : errors) {
                getLog().warn("    " + error);
            }
        }

        // ── Section 2: Component status ─────────────────────────────
        getLog().info("");
        getLog().info("  Status");
        getLog().info("  ──────────────────────────────────────────────────────");
        getLog().info(String.format("  %-24s %-24s %-8s %s",
                "COMPONENT", "BRANCH", "SHA", ""));

        List<String> dirtyComponents = new ArrayList<>();
        List<String[]> statusRows = new ArrayList<>();
        int cloned = 0;
        int notCloned = 0;

        for (var entry : graph.manifest().components().entrySet()) {
            String name = entry.getKey();
            Component comp = entry.getValue();
            File dir = new File(root, name);

            if (!dir.exists()) {
                notCloned++;
                getLog().info(String.format("  %-24s %-24s %-8s %s",
                        name, "—", "—", "not cloned"));
                statusRows.add(new String[]{name, "—", "—", "not cloned"});
                continue;
            }

            cloned++;
            String branch = gitBranch(dir);
            String sha = gitShortSha(dir);
            String status = gitStatus(dir);

            String marker;
            if (status.isEmpty()) {
                marker = "✓";
            } else {
                long count = status.lines().count();
                marker = "✗ " + count + " changed";
                dirtyComponents.add(name);
            }

            // Branch mismatch warning
            String branchCol = branch;
            if (comp.branch() != null && !branch.equals(comp.branch())) {
                branchCol = branch + " ⚠";
            }

            getLog().info(String.format("  %-24s %-24s %-8s %s",
                    name, branchCol, sha, marker));
            statusRows.add(new String[]{name, branchCol, sha, marker});
        }

        getLog().info("");
        getLog().info("  " + cloned + " cloned, " + notCloned + " not cloned, "
                + dirtyComponents.size() + " modified");

        // ── Section 3: Cascade from dirty ───────────────────────────
        List<String[]> cascadeRows = new ArrayList<>();
        if (!dirtyComponents.isEmpty()) {
            Set<String> allAffected = new LinkedHashSet<>();
            for (String dirty : dirtyComponents) {
                allAffected.addAll(graph.cascade(dirty));
            }
            // Remove components that are themselves dirty (already known)
            allAffected.removeAll(dirtyComponents);

            if (!allAffected.isEmpty()) {
                getLog().info("");
                getLog().info("  Cascade — components needing rebuild:");
                getLog().info("  ──────────────────────────────────────────────────────");

                List<String> buildOrder = graph.topologicalSort(
                        new LinkedHashSet<>(allAffected));
                for (String name : buildOrder) {
                    // Show which dirty component triggers this
                    List<String> triggeredBy = new ArrayList<>();
                    Component comp = graph.manifest().components().get(name);
                    if (comp != null) {
                        for (Dependency dep : comp.dependsOn()) {
                            if (dirtyComponents.contains(dep.component())
                                    || allAffected.contains(dep.component())) {
                                triggeredBy.add(dep.component());
                            }
                        }
                    }
                    String triggers = String.join(", ", triggeredBy);
                    getLog().info("    " + name + " ← " + triggers);
                    cascadeRows.add(new String[]{name, triggers});
                }
            }
        }

        getLog().info("");

        // Structured markdown report
        appendReport("ws:dashboard", buildMarkdownReport(
                errors, statusRows, cascadeRows,
                cloned, notCloned, dirtyComponents.size()));
    }

    private String buildMarkdownReport(List<String> manifestErrors,
                                        List<String[]> statusRows,
                                        List<String[]> cascadeRows,
                                        int cloned, int notCloned, int dirty) {
        var sb = new StringBuilder();

        // Manifest health
        if (manifestErrors.isEmpty()) {
            sb.append("Manifest: consistent.\n\n");
        } else {
            sb.append("Manifest: ").append(manifestErrors.size())
              .append(" error(s).\n\n");
        }

        // Status table
        sb.append(cloned).append(" cloned, ").append(notCloned)
          .append(" not cloned, ").append(dirty).append(" modified.\n\n");
        sb.append("| Component | Branch | SHA | Status |\n");
        sb.append("|-----------|--------|-----|--------|\n");
        for (String[] row : statusRows) {
            sb.append("| ").append(row[0])
              .append(" | ").append(row[1])
              .append(" | ").append(row[2])
              .append(" | ").append(row[3])
              .append(" |\n");
        }

        // Cascade table
        if (!cascadeRows.isEmpty()) {
            sb.append("\n**Cascade — components needing rebuild:**\n\n");
            sb.append("| Component | Triggered By |\n");
            sb.append("|-----------|-------------|\n");
            for (String[] row : cascadeRows) {
                sb.append("| ").append(row[0])
                  .append(" | ").append(row[1])
                  .append(" |\n");
            }
        }

        return sb.toString();
    }
}
