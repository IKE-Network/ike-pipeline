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
        getLog().info("  ike:release                                     Full release + bump to next SNAPSHOT");
        getLog().info("  ike:checkpoint                                  Create immutable checkpoint");
        getLog().info("  ike:deploy-site                                 Deploy site to versioned URL");
        getLog().info("  ike:release-from-feature                       Release from feature branch (stub)");
        getLog().info("  ike:workspace                                  Manage IKE Workspaces (stub)");
        getLog().info("  ike:merge-to-main                              Merge feature to main (stub)");
        getLog().info("");
        getLog().info("Options for ike:release:");
        getLog().info("  -DreleaseVersion=<v>   Version to release (auto-derived from POM)");
        getLog().info("  -DnextVersion=<v>      Next SNAPSHOT (auto-derived)");
        getLog().info("  -DdryRun=true          Show plan without executing");
        getLog().info("  -DskipVerify=true      Skip 'mvnw clean verify'");
        getLog().info("  -DallowBranch=<name>   Allow release from non-main branch");
        getLog().info("  -DdeploySite=false     Skip site deployment");
        getLog().info("");
        getLog().info("Options for ike:checkpoint:");
        getLog().info("  -DdryRun=true          Show plan without executing");
        getLog().info("  -DskipVerify=true      Skip 'mvnw clean verify'");
        getLog().info("  -DdeploySite=false     Skip site deployment");
        getLog().info("  -DcheckpointLabel=<v>  Custom checkpoint version label");
        getLog().info("");
        getLog().info("Options for ike:deploy-site:");
        getLog().info("  -DsiteType=<type>      One of: release, snapshot, checkpoint");
        getLog().info("  -DsiteVersion=<v>      Version for checkpoint URL path");
        getLog().info("  -DdryRun=true          Show plan without executing");
        getLog().info("  -DskipBuild=true       Skip 'mvnw clean verify'");
        getLog().info("");
    }
}
