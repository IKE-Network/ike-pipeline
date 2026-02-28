package network.ike.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.time.Instant;

/**
 * Prepare and deploy a release from the main branch.
 *
 * <p>This goal automates the full release workflow:
 * <ol>
 *   <li>Validate prerequisites (branch, clean worktree)</li>
 *   <li>Create {@code release/<version>} branch</li>
 *   <li>Set POM version to release version</li>
 *   <li>Build and verify</li>
 *   <li>Commit, tag, deploy to Nexus</li>
 *   <li>Push tag, create GitHub Release</li>
 *   <li>Merge back to main, push, clean up</li>
 * </ol>
 *
 * <p>Usage: {@code mvn ike:prepare-release -DreleaseVersion=2}
 *
 * @see PostReleaseMojo
 */
@Mojo(name = "prepare-release", requiresProject = false, aggregator = true)
public class PrepareReleaseMojo extends AbstractMojo {

    @Parameter(property = "releaseVersion", required = true)
    private String releaseVersion;

    @Parameter(property = "dryRun", defaultValue = "false")
    private boolean dryRun;

    @Parameter(property = "skipVerify", defaultValue = "false")
    private boolean skipVerify;

    @Parameter(property = "allowBranch")
    private String allowBranch;

    @Override
    public void execute() throws MojoExecutionException {
        File gitRoot = ReleaseSupport.gitRoot(new File("."));
        File mvnw = ReleaseSupport.resolveMavenWrapper(gitRoot);
        File rootPom = new File(gitRoot, "pom.xml");

        // Reject SNAPSHOT versions
        if (releaseVersion.contains("-SNAPSHOT")) {
            throw new MojoExecutionException(
                    "Release version must not contain -SNAPSHOT.");
        }

        // Validate branch
        String currentBranch = ReleaseSupport.currentBranch(gitRoot);
        String expectedBranch = allowBranch != null ? allowBranch : "main";
        if (!currentBranch.equals(expectedBranch)) {
            throw new MojoExecutionException(
                    "Must be on '" + expectedBranch + "' branch (currently on '" +
                            currentBranch + "'). Use -DallowBranch=" +
                            currentBranch + " to override.");
        }

        // Check release branch doesn't already exist
        String releaseBranch = "release/" + releaseVersion;
        try {
            ReleaseSupport.execCapture(gitRoot,
                    "git", "rev-parse", "--verify", releaseBranch);
            throw new MojoExecutionException(
                    "Branch '" + releaseBranch + "' already exists locally.");
        } catch (MojoExecutionException e) {
            if (e.getMessage().startsWith("Branch '")) throw e;
            // Expected — branch does not exist
        }

        // Read current version
        String oldVersion = ReleaseSupport.readPomVersion(rootPom);

        // Build environment audit
        logAudit(gitRoot, mvnw, currentBranch, releaseBranch, oldVersion);

        if (dryRun) {
            getLog().info("[DRY RUN] Would create branch: " + releaseBranch);
            getLog().info("[DRY RUN] Would set version: " + oldVersion +
                    " -> " + releaseVersion);
            getLog().info("[DRY RUN] Would run: mvnw clean verify -B");
            getLog().info("[DRY RUN] Would commit, tag v" + releaseVersion);
            getLog().info("[DRY RUN] Would deploy to Nexus: mvnw deploy -B -DskipTests");
            getLog().info("[DRY RUN] Would push tag and create GitHub Release");
            getLog().info("[DRY RUN] Would merge " + releaseBranch + " to main and push");
            return;
        }

        // Validate clean worktree (skip for dry run)
        ReleaseSupport.requireCleanWorktree(gitRoot);

        // Create release branch
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "checkout", "-b", releaseBranch);

        // Set version
        getLog().info("Setting version: " + oldVersion + " -> " + releaseVersion);
        ReleaseSupport.setPomVersion(rootPom, oldVersion, releaseVersion);

        // Verify build
        if (!skipVerify) {
            ReleaseSupport.exec(gitRoot, getLog(),
                    mvnw.getAbsolutePath(), "clean", "verify", "-B");
        } else {
            getLog().info("Skipping verify (-DskipVerify=true)");
        }

        // Commit
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "add", "pom.xml");
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "commit", "-m",
                "release: set version to " + releaseVersion);

        // Tag
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "tag", "-a", "v" + releaseVersion,
                "-m", "Release " + releaseVersion);

        // Deploy
        ReleaseSupport.exec(gitRoot, getLog(),
                mvnw.getAbsolutePath(), "deploy", "-B", "-DskipTests",
                "-P", "release,signArtifacts");

        // Push tag
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "push", "origin", "v" + releaseVersion);

        // Create GitHub Release (graceful fallback if gh not available)
        try {
            ReleaseSupport.exec(gitRoot, getLog(),
                    "gh", "release", "create", "v" + releaseVersion,
                    "--title", releaseVersion,
                    "--generate-notes", "--verify-tag");
        } catch (MojoExecutionException e) {
            getLog().warn("GitHub Release creation failed (gh CLI may not be installed): " +
                    e.getMessage());
            getLog().warn("Run manually: gh release create v" + releaseVersion +
                    " --title " + releaseVersion + " --generate-notes");
        }

        // Merge back to main
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "checkout", "main");
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "merge", "--no-ff", releaseBranch,
                "-m", "merge: release " + releaseVersion);
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "push", "origin", "main");

        // Clean up release branch
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "branch", "-d", releaseBranch);

        getLog().info("");
        getLog().info("Release " + releaseVersion + " complete.");
        getLog().info("  Tagged: v" + releaseVersion);
        getLog().info("  Deployed to Nexus");
        getLog().info("  Merged to main");
        getLog().info("");
        getLog().info("Next: mvn ike:post-release -DnextVersion=<next>-SNAPSHOT");
    }

    private void logAudit(File gitRoot, File mvnw, String branch,
                          String releaseBranch, String oldVersion)
            throws MojoExecutionException {
        String gitCommit = ReleaseSupport.execCapture(gitRoot,
                "git", "rev-parse", "--short", "HEAD");
        String mavenVersion = ReleaseSupport.execCapture(gitRoot,
                mvnw.getAbsolutePath(), "--version");
        String javaVersion = System.getProperty("java.version", "unknown");

        getLog().info("");
        getLog().info("RELEASE PARAMETERS");
        getLog().info("  Version:        " + oldVersion + " -> " + releaseVersion);
        getLog().info("  Source branch:  " + branch);
        getLog().info("  Release branch: " + releaseBranch);
        getLog().info("  Tag:            v" + releaseVersion);
        getLog().info("  Skip verify:    " + skipVerify);
        getLog().info("  Dry run:        " + dryRun);
        getLog().info("");
        getLog().info("BUILD ENVIRONMENT");
        getLog().info("  Date:           " + Instant.now());
        getLog().info("  User:           " + System.getProperty("user.name", "unknown"));
        getLog().info("  Git commit:     " + gitCommit);
        getLog().info("  Git root:       " + gitRoot.getAbsolutePath());
        getLog().info("  Maven wrapper:  " + mvnw.getAbsolutePath());
        getLog().info("  Maven version:  " + mavenVersion.lines().findFirst().orElse("unknown"));
        getLog().info("  Java version:   " + javaVersion);
        getLog().info("  OS:             " + System.getProperty("os.name") + " " +
                System.getProperty("os.arch"));
        getLog().info("");
    }
}
