package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;

import network.ike.workspace.Subproject;
import network.ike.workspace.ManifestWriter;
import network.ike.workspace.WorkspaceGraph;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Synchronize workspace.yaml branch fields with actual git branches.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Default</b> (from=repos): read actual branches from each
 *       cloned subproject and update workspace.yaml to match reality.</li>
 *   <li><b>from=manifest</b>: read workspace.yaml branch fields and
 *       switch each cloned subproject to the declared branch.</li>
 * </ul>
 *
 * <pre>{@code
 * mvn ike:ws-sync                    # update yaml from repos
 * mvn ike:ws-sync -Dfrom=manifest    # switch repos to match yaml
 * }</pre>
 */
@Mojo(name = "sync", projectRequired = false)
public class WsSyncMojo extends AbstractWorkspaceMojo {

    /**
     * Sync direction: {@code repos} (default) updates workspace.yaml
     * from actual branches; {@code manifest} switches repos to match
     * workspace.yaml.
     */
    @Parameter(property = "from", defaultValue = "repos")
    String from;

    /** Show plan without executing. */
    @Parameter(property = "publish", defaultValue = "true")
    boolean publish;

    /** Creates this goal instance. */
    public WsSyncMojo() {}

    @Override
    public void execute() throws MojoException {
        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();
        Path manifestPath = resolveManifest();
        boolean draft = !publish;

        getLog().info("");
        getLog().info("IKE Workspace \u2014 Sync");
        getLog().info("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        getLog().info("  Direction: " + ("manifest".equals(from) ? "manifest \u2192 repos" : "repos \u2192 manifest"));
        if (draft) {
            getLog().info("  Mode:      DRAFT");
        }
        getLog().info("");

        if ("manifest".equals(from)) {
            syncFromManifest(graph, root);
        } else {
            syncFromRepos(graph, root, manifestPath);
        }

        writeReport(WsGoal.SYNC, "**Direction:** "
                + ("manifest".equals(from) ? "manifest → repos" : "repos → manifest")
                + "\n");

        getLog().info("");
    }

    /**
     * Read actual branches and update workspace.yaml.
     */
    private void syncFromRepos(WorkspaceGraph graph, File root, Path manifestPath)
            throws MojoException {
        Map<String, String> updates = new LinkedHashMap<>();
        int unchanged = 0;

        for (Map.Entry<String, Subproject> entry : graph.manifest().subprojects().entrySet()) {
            String name = entry.getKey();
            Subproject subproject = entry.getValue();
            File dir = new File(root, name);

            if (!new File(dir, ".git").exists()) {
                continue;
            }

            String actual = gitBranch(dir);
            String declared = subproject.branch();

            if (actual.equals(declared)) {
                unchanged++;
            } else {
                updates.put(name, actual);
                getLog().info("  " + name + ": " + declared + " \u2192 " + actual);
            }
        }

        if (updates.isEmpty()) {
            getLog().info("  All branches match workspace.yaml (" + unchanged + " components)");
            return;
        }

        if (publish) {
            try {
                ManifestWriter.updateBranches(manifestPath, updates);
                getLog().info("  Updated workspace.yaml (" + updates.size() + " changes)");

                // Commit if workspace is a git repo
                File wsRoot = manifestPath.getParent().toFile();
                if (new File(wsRoot, ".git").exists()) {
                    ReleaseSupport.exec(wsRoot, getLog(),
                            "git", "add", "workspace.yaml");
                    ReleaseSupport.exec(wsRoot, getLog(),
                            "git", "commit", "-m",
                            "workspace: sync branch fields from repos");
                    getLog().info("  Committed workspace.yaml");
                }
            } catch (IOException e) {
                throw new MojoException(
                        "Failed to update workspace.yaml: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Read workspace.yaml and switch repos to declared branches.
     */
    private void syncFromManifest(WorkspaceGraph graph, File root)
            throws MojoException {
        int switched = 0;
        int unchanged = 0;

        for (Map.Entry<String, Subproject> entry : graph.manifest().subprojects().entrySet()) {
            String name = entry.getKey();
            Subproject subproject = entry.getValue();
            File dir = new File(root, name);

            if (!new File(dir, ".git").exists()) {
                continue;
            }

            String actual = gitBranch(dir);
            String declared = subproject.branch();

            if (declared == null || actual.equals(declared)) {
                unchanged++;
                continue;
            }

            // Check for uncommitted changes
            String status = gitStatus(dir);
            if (!status.isEmpty()) {
                getLog().warn("  \u26A0 " + name + ": has uncommitted changes, skipping");
                continue;
            }

            getLog().info("  " + name + ": " + actual + " \u2192 " + declared);

            if (publish) {
                ReleaseSupport.exec(dir, getLog(),
                        "git", "checkout", declared);
                switched++;
            }
        }

        getLog().info("  Switched: " + switched + " | Unchanged: " + unchanged);
    }
}
