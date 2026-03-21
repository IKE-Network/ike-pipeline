package network.ike.plugin;

import network.ike.workspace.Component;
import network.ike.workspace.Dependency;
import network.ike.workspace.WorkspaceGraph;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.List;

/**
 * Print the workspace dependency graph.
 *
 * <p>Displays all components in topological order with their
 * dependencies. Optionally outputs DOT format for Graphviz rendering.
 *
 * <pre>{@code
 * mvn ike:graph
 * mvn ike:graph -Dformat=dot
 * }</pre>
 */
@Mojo(name = "graph", requiresProject = false, threadSafe = true)
public class GraphWorkspaceMojo extends AbstractWorkspaceMojo {

    /**
     * Output format: "text" (default) or "dot" (Graphviz DOT).
     */
    @Parameter(property = "format", defaultValue = "text")
    private String format;

    @Override
    public void execute() throws MojoExecutionException {
        WorkspaceGraph graph = loadGraph();

        if ("dot".equalsIgnoreCase(format)) {
            printDot(graph);
        } else {
            printText(graph);
        }
    }

    private void printText(WorkspaceGraph graph) {
        getLog().info("");
        getLog().info("IKE Workspace — Dependency Graph");
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("");

        List<String> sorted = graph.topologicalSort();

        for (int i = 0; i < sorted.size(); i++) {
            String name = sorted.get(i);
            Component comp = graph.manifest().components().get(name);

            getLog().info(String.format("  %2d. %-28s [%s]",
                    i + 1, name, comp.type()));

            if (!comp.dependsOn().isEmpty()) {
                for (Dependency dep : comp.dependsOn()) {
                    getLog().info(String.format("        └─ %s (%s)",
                            dep.component(), dep.relationship()));
                }
            }
        }

        getLog().info("");
        getLog().info("  " + sorted.size() + " components in dependency order.");
        getLog().info("");
    }

    private void printDot(WorkspaceGraph graph) {
        // Output DOT to info log — can be piped to dot or Kroki
        getLog().info("digraph workspace {");
        getLog().info("    rankdir=BT;");
        getLog().info("    node [shape=box, style=rounded, fontname=\"Helvetica\"];");
        getLog().info("");

        // Style by type
        for (var entry : graph.manifest().componentTypes().entrySet()) {
            String typeName = entry.getKey();
            String color = switch (typeName) {
                case "infrastructure" -> "#e8d5b7";
                case "software"       -> "#b7d5e8";
                case "document"       -> "#b7e8c4";
                case "knowledge-source" -> "#e8b7d5";
                case "template"       -> "#d5d5d5";
                default               -> "#ffffff";
            };
            for (Component comp : graph.manifest().components().values()) {
                if (comp.type().equals(typeName)) {
                    getLog().info("    \"" + comp.name()
                            + "\" [fillcolor=\"" + color
                            + "\", style=\"rounded,filled\"];");
                }
            }
        }

        getLog().info("");

        // Edges
        for (Component comp : graph.manifest().components().values()) {
            for (Dependency dep : comp.dependsOn()) {
                String style = "content".equals(dep.relationship())
                        ? " [style=dashed]" : "";
                getLog().info("    \"" + comp.name() + "\" -> \""
                        + dep.component() + "\"" + style + ";");
            }
        }

        getLog().info("}");
    }
}
