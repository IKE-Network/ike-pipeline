package network.ike.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

/**
 * Generate and deploy the Maven site to a versioned URL.
 *
 * <p>This goal deploys the project site to one of three location
 * types under {@code ike.komet.sh}:
 * <ul>
 *   <li>{@code release} — overwritten on each release</li>
 *   <li>{@code snapshot} — overwritten on each snapshot deploy</li>
 *   <li>{@code checkpoint} — immutable, versioned subdirectory</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * mvn ike:deploy-site -DsiteType=snapshot
 * mvn ike:deploy-site -DsiteType=checkpoint -DsiteVersion=7-checkpoint.20260228.1
 * </pre>
 */
@Mojo(name = "deploy-site", requiresProject = false, aggregator = true)
public class DeploySiteMojo extends AbstractMojo {

    private static final String SITE_BASE = "scpexe://proxy/srv/ike-site/";

    @Parameter(property = "siteType", required = true)
    private String siteType;

    @Parameter(property = "siteVersion")
    private String siteVersion;

    @Parameter(property = "dryRun", defaultValue = "false")
    private boolean dryRun;

    @Parameter(property = "skipBuild", defaultValue = "false")
    private boolean skipBuild;

    @Override
    public void execute() throws MojoExecutionException {
        File gitRoot = ReleaseSupport.gitRoot(new File("."));
        File mvnw = ReleaseSupport.resolveMavenWrapper(gitRoot);
        File rootPom = new File(gitRoot, "pom.xml");

        String projectId = ReleaseSupport.readPomArtifactId(rootPom);

        // Default siteVersion from POM version
        if (siteVersion == null || siteVersion.isBlank()) {
            siteVersion = ReleaseSupport.readPomVersion(rootPom);
        }

        // Resolve target URL
        String targetUrl = switch (siteType) {
            case "release" -> SITE_BASE + projectId + "/release";
            case "snapshot" -> SITE_BASE + projectId + "/snapshot";
            case "checkpoint" -> SITE_BASE + projectId + "/checkpoint/" + siteVersion;
            default -> throw new MojoExecutionException(
                    "Invalid siteType: '" + siteType +
                            "'. Must be one of: release, snapshot, checkpoint");
        };

        getLog().info("");
        getLog().info("SITE DEPLOYMENT");
        getLog().info("  Project:    " + projectId);
        getLog().info("  Site type:  " + siteType);
        getLog().info("  Version:    " + siteVersion);
        getLog().info("  Target URL: " + targetUrl);
        getLog().info("  Skip build: " + skipBuild);
        getLog().info("  Dry run:    " + dryRun);
        getLog().info("");

        if (dryRun) {
            if (!skipBuild) {
                getLog().info("[DRY RUN] Would run: mvnw clean verify -B");
            }
            getLog().info("[DRY RUN] Would run: mvnw site site:stage site:deploy -B -Dsite.deploy.url=" + targetUrl);
            return;
        }

        // Build first (unless skipped)
        if (!skipBuild) {
            ReleaseSupport.exec(gitRoot, getLog(),
                    mvnw.getAbsolutePath(), "clean", "verify", "-B");
        }

        // Generate, stage, and deploy site in a single reactor pass.
        // Override site.deploy.url to control the deployment target —
        // this feeds into <distributionManagement><site><url>.
        ReleaseSupport.exec(gitRoot, getLog(),
                mvnw.getAbsolutePath(), "site", "site:stage", "site:deploy", "-B",
                "-Dsite.deploy.url=" + targetUrl);

        getLog().info("");
        getLog().info("Site deployed to: " + targetUrl.replace("scpexe://proxy/srv/ike-site",
                "http://ike.komet.sh"));
    }
}
