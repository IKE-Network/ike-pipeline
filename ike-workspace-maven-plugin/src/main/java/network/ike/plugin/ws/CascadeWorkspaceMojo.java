package network.ike.plugin.ws;

import network.ike.workspace.Component;
import network.ike.workspace.WorkspaceGraph;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

import java.util.List;

/**
 * Compute the cascade (propagation set) from a changed component.
 *
 * <p>Shows all components that transitively depend on the given
 * component and would need rebuilding after a change.
 *
 * <pre>{@code
 * mvn ike:cascade -Dcomponent=tinkar-core
 * }</pre>
 */
@Mojo(name = "cascade", projectRequired = false)
public class CascadeWorkspaceMojo extends AbstractWorkspaceMojo {

    /**
     * The component that changed. Prompted if omitted.
     */
    @Parameter(property = "component")
    String component;

    /** Creates this goal instance. */
    public CascadeWorkspaceMojo() {}

    @Override
    public void execute() throws MojoException {
        component = requireParam(component, "component", "Component that changed");

        WorkspaceGraph graph = loadGraph();

        getLog().info("");
        getLog().info(header("Cascade from: " + component));
        getLog().info("══════════════════════════════════════════════════════════════");

        List<String> affected = graph.cascade(component);

        if (affected.isEmpty()) {
            getLog().info("");
            getLog().info("  " + component + " is a leaf — no downstream dependents.");
        } else {
            getLog().info("");
            getLog().info("  Affected components (" + affected.size() + "):");
            getLog().info("");

            // Show in cascade order with type and relationship
            for (int i = 0; i < affected.size(); i++) {
                String name = affected.get(i);
                Component comp = graph.manifest().components().get(name);
                String type = comp != null ? comp.type() : "?";
                getLog().info(String.format("    %2d. %-28s [%s]",
                        i + 1, name, type));
            }

            // Show build order
            getLog().info("");
            getLog().info("  Rebuild order (topological):");
            getLog().info("");

            List<String> buildOrder = graph.topologicalSort(
                    new java.util.LinkedHashSet<>(affected));
            for (int i = 0; i < buildOrder.size(); i++) {
                String name = buildOrder.get(i);
                Component comp = graph.manifest().components().get(name);
                String cmd = "";
                if (comp != null) {
                    var ct = graph.manifest().componentTypes().get(comp.type());
                    cmd = ct != null ? ct.buildCommand() : "";
                }
                getLog().info(String.format("    %2d. %-28s %s",
                        i + 1, name, cmd));
            }
        }

        getLog().info("");

        // Report
        var sb = new StringBuilder();
        sb.append("**Component:** `").append(component).append("`\n\n");
        if (affected.isEmpty()) {
            sb.append("No downstream dependencies — leaf component.\n");
        } else {
            sb.append("| # | Affected Component |\n");
            sb.append("|---|--------------------|\n");
            List<String> buildOrder = graph.topologicalSort(
                    new java.util.LinkedHashSet<>(affected));
            for (int i = 0; i < buildOrder.size(); i++) {
                sb.append("| ").append(i + 1)
                  .append(" | ").append(buildOrder.get(i)).append(" |\n");
            }
        }
        writeReport("ws:cascade", sb.toString());
    }
}
