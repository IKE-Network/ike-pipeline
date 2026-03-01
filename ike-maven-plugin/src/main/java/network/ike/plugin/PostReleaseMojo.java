package network.ike.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.time.Instant;

/**
 * Bump main to the next SNAPSHOT version after a release.
 *
 * <p>This goal automates the post-release workflow:
 * <ol>
 *   <li>Validate prerequisites (on main, clean worktree)</li>
 *   <li>Pull latest main</li>
 *   <li>Set POM version to next SNAPSHOT</li>
 *   <li>Verify the build</li>
 *   <li>Commit and push</li>
 * </ol>
 *
 * <p>Usage: {@code mvn ike:post-release} (defaults to current
 * version incremented with {@code -SNAPSHOT}), or override with
 * {@code mvn ike:post-release -DnextVersion=3-SNAPSHOT}
 *
 * @see PrepareReleaseMojo
 */
@Mojo(name = "post-release", requiresProject = false, aggregator = true)
public class PostReleaseMojo extends AbstractMojo {

    @Parameter(property = "nextVersion")
    private String nextVersion;

    @Parameter(property = "dryRun", defaultValue = "false")
    private boolean dryRun;

    @Override
    public void execute() throws MojoExecutionException {
        File gitRoot = ReleaseSupport.gitRoot(new File("."));
        File mvnw = ReleaseSupport.resolveMavenWrapper(gitRoot, getLog());
        File rootPom = new File(gitRoot, "pom.xml");

        // Default nextVersion from current POM version
        if (nextVersion == null || nextVersion.isBlank()) {
            String pomVersion = ReleaseSupport.readPomVersion(rootPom);
            nextVersion = ReleaseSupport.deriveNextSnapshot(pomVersion);
            getLog().info("No -DnextVersion specified; defaulting to: " + nextVersion);
        }

        // Enforce SNAPSHOT suffix
        if (!nextVersion.endsWith("-SNAPSHOT")) {
            throw new MojoExecutionException(
                    "Next version must end with -SNAPSHOT (got '" + nextVersion + "'). " +
                            "Example: -DnextVersion=" + nextVersion + "-SNAPSHOT");
        }

        // Validate branch
        String currentBranch = ReleaseSupport.currentBranch(gitRoot);
        if (!"main".equals(currentBranch)) {
            throw new MojoExecutionException(
                    "Must be on 'main' branch (currently on '" + currentBranch + "').");
        }

        // Read current version
        String oldVersion = ReleaseSupport.readPomVersion(rootPom);

        // Build environment audit
        logAudit(gitRoot, mvnw, currentBranch, oldVersion);

        // Validate clean worktree (before dry run so it catches problems early)
        ReleaseSupport.requireCleanWorktree(gitRoot);

        if (dryRun) {
            getLog().info("[DRY RUN] Would pull latest main");
            getLog().info("[DRY RUN] Would set version: " + oldVersion +
                    " -> " + nextVersion);
            getLog().info("[DRY RUN] Would run: mvnw clean verify -B");
            getLog().info("[DRY RUN] Would commit and push");
            return;
        }

        boolean hasOrigin = ReleaseSupport.hasRemote(gitRoot, "origin");

        // Pull latest (skip if no remote)
        if (hasOrigin) {
            ReleaseSupport.exec(gitRoot, getLog(),
                    "git", "pull", "origin", "main");
        } else {
            getLog().info("No 'origin' remote — skipping pull");
        }

        // Re-read version after pull (merge may have changed it)
        String currentVersion = ReleaseSupport.readPomVersion(rootPom);

        // Set version
        getLog().info("Setting version: " + currentVersion + " -> " + nextVersion);
        ReleaseSupport.setPomVersion(rootPom, currentVersion, nextVersion);

        // Verify build
        ReleaseSupport.exec(gitRoot, getLog(),
                mvnw.getAbsolutePath(), "clean", "verify", "-B");

        // Commit and push
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "add", "pom.xml");
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "commit", "-m",
                "post-release: bump to " + nextVersion);
        if (hasOrigin) {
            ReleaseSupport.exec(gitRoot, getLog(),
                    "git", "push", "origin", "main");
        } else {
            getLog().info("No 'origin' remote — skipping push to main");
        }

        getLog().info("");
        getLog().info("main is now at " + nextVersion);
    }

    private void logAudit(File gitRoot, File mvnw, String branch,
                          String oldVersion) throws MojoExecutionException {
        String gitCommit = ReleaseSupport.execCapture(gitRoot,
                "git", "rev-parse", "--short", "HEAD");
        String mavenVersion = ReleaseSupport.execCapture(gitRoot,
                mvnw.getAbsolutePath(), "--version");

        getLog().info("");
        getLog().info("POST-RELEASE VERSION BUMP");
        getLog().info("  Version:       " + oldVersion + " -> " + nextVersion);
        getLog().info("  Branch:        " + branch);
        getLog().info("  Dry run:       " + dryRun);
        getLog().info("");
        getLog().info("BUILD ENVIRONMENT");
        getLog().info("  Date:          " + Instant.now());
        getLog().info("  User:          " + System.getProperty("user.name", "unknown"));
        getLog().info("  Git commit:    " + gitCommit);
        getLog().info("  Git root:      " + gitRoot.getAbsolutePath());
        getLog().info("  Maven wrapper: " + mvnw.getAbsolutePath());
        getLog().info("  Maven version: " + mavenVersion.lines().findFirst().orElse("unknown"));
        getLog().info("  OS:            " + System.getProperty("os.name") + " " +
                System.getProperty("os.arch"));
        getLog().info("");
    }
}
