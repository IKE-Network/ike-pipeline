package network.ike.plugin;

import network.ike.workspace.Manifest;
import network.ike.workspace.ManifestException;
import network.ike.workspace.ManifestReader;
import network.ike.workspace.WorkspaceGraph;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Path;

/**
 * Base class for workspace goals that read {@code workspace.yaml}.
 *
 * <p>Resolves the manifest by searching upward from the invocation
 * directory for a file named {@code workspace.yaml}. All workspace
 * goals inherit this resolution logic.
 */
abstract class AbstractWorkspaceMojo extends AbstractMojo {

    /**
     * Path to workspace.yaml. If not set, searches upward from the
     * current directory.
     */
    @Parameter(property = "workspace.manifest")
    private File manifest;

    /**
     * Load the manifest and build the workspace graph.
     */
    protected WorkspaceGraph loadGraph() throws MojoExecutionException {
        Path manifestPath = resolveManifest();
        getLog().debug("Reading manifest: " + manifestPath);
        try {
            Manifest m = ManifestReader.read(manifestPath);
            return new WorkspaceGraph(m);
        } catch (ManifestException e) {
            throw new MojoExecutionException(
                    "Failed to read workspace manifest: " + e.getMessage(), e);
        }
    }

    /**
     * Resolve the manifest path — explicit parameter, or search upward.
     */
    protected Path resolveManifest() throws MojoExecutionException {
        if (manifest != null && manifest.exists()) {
            return manifest.toPath();
        }

        // Search upward from current directory
        Path dir = Path.of(System.getProperty("user.dir"));
        while (dir != null) {
            Path candidate = dir.resolve("workspace.yaml");
            if (candidate.toFile().exists()) {
                return candidate;
            }
            dir = dir.getParent();
        }

        throw new MojoExecutionException(
                "Cannot find workspace.yaml. Specify -Dworkspace.manifest=<path> " +
                        "or run from within a workspace directory.");
    }

    /**
     * Resolve the workspace root directory (parent of workspace.yaml).
     */
    protected File workspaceRoot() throws MojoExecutionException {
        return resolveManifest().getParent().toFile();
    }

    /**
     * Run {@code git status --porcelain} on a component directory and
     * return the output (empty string = clean).
     */
    protected String gitStatus(File componentDir) {
        try {
            return ReleaseSupport.execCapture(componentDir,
                    "git", "status", "--porcelain");
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Get the current branch of a component directory.
     */
    protected String gitBranch(File componentDir) {
        try {
            return ReleaseSupport.execCapture(componentDir,
                    "git", "rev-parse", "--abbrev-ref", "HEAD");
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Get the short SHA of HEAD for a component directory.
     */
    protected String gitShortSha(File componentDir) {
        try {
            return ReleaseSupport.execCapture(componentDir,
                    "git", "rev-parse", "--short", "HEAD");
        } catch (Exception e) {
            return "???????";
        }
    }
}
