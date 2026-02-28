package network.ike.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Create and manage IKE Workspaces.
 *
 * <p>Not yet implemented. Use the bash script fallback:
 * {@code target/build-tools/scripts/ike-workspace.sh}
 */
@Mojo(name = "workspace", requiresProject = false)
public class WorkspaceMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        getLog().warn("ike:workspace is not yet implemented.");
        getLog().info("Fallback: target/build-tools/scripts/ike-workspace.sh");
    }
}
