package network.ike.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Displays available IKE build tool goals.
 *
 * <p>Each goal wraps a bash script from ike-build-tools.
 * The Maven plugin provides IDE discoverability;
 * the bash scripts hold the logic.
 *
 * @see <a href="https://github.com/IKE-Network/ike-pipeline">IKE Pipeline</a>
 */
@Mojo(name = "help", requiresProject = false)
public class IkeHelpMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("");
        getLog().info("IKE Build Tools — Available Goals");
        getLog().info("══════════════════════════════════");
        getLog().info("  ike:help              This help message");
        getLog().info("  ike:workspace         Create/manage IKE Workspaces (planned)");
        getLog().info("  ike:checkpoint        Create immutable checkpoint (planned)");
        getLog().info("  ike:merge-to-main     Merge feature to main (planned)");
        getLog().info("  ike:prepare-release   Prepare a release (planned)");
        getLog().info("  ike:post-release      Bump to next SNAPSHOT (planned)");
        getLog().info("");
        getLog().info("Each goal wraps a bash script in ike-build-tools.");
        getLog().info("Scripts can also be invoked directly from the command line.");
        getLog().info("");
    }
}
