package network.ike.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Displays available IKE build tool goals.
 *
 * @see <a href="https://github.com/IKE-Network/ike-pipeline">IKE Pipeline</a>
 */
@Mojo(name = "help", requiresProject = false)
public class IkeHelpMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("");
        getLog().info("IKE Build Tools — Available Goals");
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("");
        getLog().info("  ike:help                                        This help message");
        getLog().info("  ike:prepare-release -DreleaseVersion=2          Prepare and deploy a release");
        getLog().info("  ike:post-release    -DnextVersion=3-SNAPSHOT    Bump to next SNAPSHOT");
        getLog().info("  ike:release-from-feature                        Release from feature branch (stub)");
        getLog().info("  ike:workspace                                   Manage IKE Workspaces (stub)");
        getLog().info("  ike:checkpoint                                  Create checkpoint (stub)");
        getLog().info("  ike:merge-to-main                               Merge feature to main (stub)");
        getLog().info("");
        getLog().info("Options for ike:prepare-release:");
        getLog().info("  -DreleaseVersion=<v>   Version to release (required)");
        getLog().info("  -DdryRun=true          Show plan without executing");
        getLog().info("  -DskipVerify=true      Skip 'mvnw clean verify'");
        getLog().info("  -DallowBranch=<name>   Allow release from non-main branch");
        getLog().info("");
        getLog().info("Options for ike:post-release:");
        getLog().info("  -DnextVersion=<v>      Next SNAPSHOT version (required)");
        getLog().info("  -DdryRun=true          Show plan without executing");
        getLog().info("");
    }
}
