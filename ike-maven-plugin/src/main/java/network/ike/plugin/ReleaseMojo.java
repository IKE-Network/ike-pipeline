package network.ike.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.time.Instant;
import java.util.List;

/**
 * Full release: build, deploy, tag, merge, and bump to next SNAPSHOT.
 *
 * <p>This goal automates the complete release workflow in one command:
 * <ol>
 *   <li>Validate prerequisites (branch, clean worktree)</li>
 *   <li>Create {@code release/<version>} branch</li>
 *   <li>Set POM version to release version</li>
 *   <li>Build, verify, commit, tag</li>
 *   <li>Deploy to Nexus (with GPG signing)</li>
 *   <li>Deploy release site</li>
 *   <li>Push tag, create GitHub Release</li>
 *   <li>Restore {@code ${project.version}}, merge to main</li>
 *   <li>Bump to next SNAPSHOT version</li>
 * </ol>
 *
 * <p>Usage: {@code mvn ike:release} (auto-derives version from POM),
 * or override with {@code mvn ike:release -DreleaseVersion=2}
 *
 * @see CheckpointMojo
 */
@Mojo(name = "release", requiresProject = false, aggregator = true, threadSafe = true)
public class ReleaseMojo extends AbstractMojo {

    @Parameter(property = "releaseVersion")
    private String releaseVersion;

    @Parameter(property = "nextVersion")
    private String nextVersion;

    @Parameter(property = "dryRun", defaultValue = "false")
    private boolean dryRun;

    @Parameter(property = "skipVerify", defaultValue = "false")
    private boolean skipVerify;

    @Parameter(property = "allowBranch")
    private String allowBranch;

    @Parameter(property = "deploySite", defaultValue = "true")
    private boolean deploySite;

    @Override
    public void execute() throws MojoExecutionException {
        File gitRoot = ReleaseSupport.gitRoot(new File("."));
        File mvnw = ReleaseSupport.resolveMavenWrapper(gitRoot, getLog());
        File rootPom = new File(gitRoot, "pom.xml");

        // Default releaseVersion from current POM version
        String oldVersion = ReleaseSupport.readPomVersion(rootPom);
        if (releaseVersion == null || releaseVersion.isBlank()) {
            releaseVersion = ReleaseSupport.deriveReleaseVersion(oldVersion);
            getLog().info("No -DreleaseVersion specified; defaulting to: " + releaseVersion);
        }

        // Default nextVersion
        if (nextVersion == null || nextVersion.isBlank()) {
            nextVersion = ReleaseSupport.deriveNextSnapshot(releaseVersion);
        }

        // Reject SNAPSHOT release versions
        if (releaseVersion.contains("-SNAPSHOT")) {
            throw new MojoExecutionException(
                    "Release version must not contain -SNAPSHOT.");
        }

        // Enforce SNAPSHOT suffix on next version
        if (!nextVersion.endsWith("-SNAPSHOT")) {
            throw new MojoExecutionException(
                    "Next version must end with -SNAPSHOT (got '" + nextVersion + "').");
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

        String projectId = ReleaseSupport.readPomArtifactId(rootPom);

        // Build environment audit
        logAudit(gitRoot, mvnw, currentBranch, releaseBranch, oldVersion, projectId);

        // Validate clean worktree
        ReleaseSupport.requireCleanWorktree(gitRoot);

        if (dryRun) {
            getLog().info("[DRY RUN] Would create branch: " + releaseBranch);
            getLog().info("[DRY RUN] Would set version: " + oldVersion +
                    " -> " + releaseVersion);
            getLog().info("[DRY RUN] Would resolve ${project.version} -> " +
                    releaseVersion + " in all POMs");
            getLog().info("[DRY RUN] Would run: mvnw clean verify -B");
            getLog().info("[DRY RUN] Would commit, tag v" + releaseVersion);
            getLog().info("[DRY RUN] Would deploy to Nexus: mvnw deploy -B -DskipTests");
            if (deploySite) {
                getLog().info("[DRY RUN] Would deploy release site to: " +
                        "scpexe://proxy/srv/ike-site/" + projectId + "/release");
            }
            getLog().info("[DRY RUN] Would push tag and create GitHub Release");
            getLog().info("[DRY RUN] Would restore ${project.version} references");
            getLog().info("[DRY RUN] Would merge " + releaseBranch + " to main and push");
            getLog().info("[DRY RUN] Would bump to next version: " + nextVersion);
            return;
        }

        // ── Release ───────────────────────────────────────────────────

        // Create release branch
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "checkout", "-b", releaseBranch);

        // Set version
        getLog().info("Setting version: " + oldVersion + " -> " + releaseVersion);
        ReleaseSupport.setPomVersion(rootPom, oldVersion, releaseVersion);

        // WORKAROUND: Maven 4 consumer POM doesn't resolve ${project.version}
        // in <build><plugins>, <pluginManagement>, or <dependencyManagement>.
        getLog().info("Resolving ${project.version} references:");
        List<File> resolvedPoms =
                ReleaseSupport.replaceProjectVersionRefs(gitRoot, releaseVersion, getLog());

        // Verify build
        if (!skipVerify) {
            ReleaseSupport.exec(gitRoot, getLog(),
                    mvnw.getAbsolutePath(), "clean", "verify", "-B");
        } else {
            getLog().info("Skipping verify (-DskipVerify=true)");
        }

        // Commit
        ReleaseSupport.exec(gitRoot, getLog(), "git", "add", "pom.xml");
        ReleaseSupport.gitAddFiles(gitRoot, getLog(), resolvedPoms);
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "commit", "-m",
                "release: set version to " + releaseVersion);

        // Tag
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "tag", "-a", "v" + releaseVersion,
                "-m", "Release " + releaseVersion);

        // Deploy to Nexus (and site in parallel if enabled)
        if (deploySite) {
            ReleaseSupport.execParallel(gitRoot, getLog(),
                    new ReleaseSupport.LabeledTask("nexus",
                            new String[]{mvnw.getAbsolutePath(), "deploy", "-B", "-DskipTests",
                                    "-P", "release,signArtifacts"}),
                    new ReleaseSupport.LabeledTask("site",
                            new String[]{mvnw.getAbsolutePath(), "site", "site:stage",
                                    "site:deploy", "-B",
                                    "-Dsite.deploy.url=scpexe://proxy/srv/ike-site/" +
                                            projectId + "/release"}));
        } else {
            ReleaseSupport.exec(gitRoot, getLog(),
                    mvnw.getAbsolutePath(), "deploy", "-B", "-DskipTests",
                    "-P", "release,signArtifacts");
        }

        // Restore ${project.version} references
        getLog().info("Restoring ${project.version} references:");
        List<File> restoredPoms = ReleaseSupport.restoreBackups(gitRoot, getLog());
        if (!restoredPoms.isEmpty()) {
            ReleaseSupport.gitAddFiles(gitRoot, getLog(), restoredPoms);
            ReleaseSupport.exec(gitRoot, getLog(),
                    "git", "commit", "-m",
                    "release: restore ${project.version} references");
        }

        boolean hasOrigin = ReleaseSupport.hasRemote(gitRoot, "origin");

        // Push tag
        if (hasOrigin) {
            ReleaseSupport.exec(gitRoot, getLog(),
                    "git", "push", "origin", "v" + releaseVersion);
        } else {
            getLog().info("No 'origin' remote — skipping tag push");
        }

        // Create GitHub Release
        if (hasOrigin) {
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
        } else {
            getLog().info("No 'origin' remote — skipping GitHub Release");
        }

        // Merge back to main
        ReleaseSupport.exec(gitRoot, getLog(), "git", "checkout", "main");
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "merge", "--no-ff", releaseBranch,
                "-m", "merge: release " + releaseVersion);
        if (hasOrigin) {
            ReleaseSupport.exec(gitRoot, getLog(),
                    "git", "push", "origin", "main");
        } else {
            getLog().info("No 'origin' remote — skipping push to main");
        }

        // Clean up release branch
        ReleaseSupport.exec(gitRoot, getLog(),
                "git", "branch", "-d", releaseBranch);

        // ── Post-release bump ─────────────────────────────────────────

        getLog().info("");
        getLog().info("Bumping to next version: " + nextVersion);

        // Re-read version after merge (it's the release version on main now)
        String currentVersion = ReleaseSupport.readPomVersion(rootPom);
        ReleaseSupport.setPomVersion(rootPom, currentVersion, nextVersion);

        // Verify build with new SNAPSHOT version
        ReleaseSupport.exec(gitRoot, getLog(),
                mvnw.getAbsolutePath(), "clean", "verify", "-B");

        // Commit and push
        ReleaseSupport.exec(gitRoot, getLog(), "git", "add", "pom.xml");
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
        getLog().info("Release " + releaseVersion + " complete.");
        getLog().info("  Tagged: v" + releaseVersion);
        getLog().info("  Deployed to Nexus");
        if (deploySite) {
            getLog().info("  Site: http://ike.komet.sh/" + projectId + "/release/");
        }
        getLog().info("  Merged to main");
        getLog().info("  Next version: " + nextVersion);
    }

    private void logAudit(File gitRoot, File mvnw, String branch,
                          String releaseBranch, String oldVersion,
                          String projectId) throws MojoExecutionException {
        String gitCommit = ReleaseSupport.execCapture(gitRoot,
                "git", "rev-parse", "--short", "HEAD");
        String mavenVersion = ReleaseSupport.execCapture(gitRoot,
                mvnw.getAbsolutePath(), "--version");
        String javaVersion = System.getProperty("java.version", "unknown");

        getLog().info("");
        getLog().info("RELEASE PARAMETERS");
        getLog().info("  Version:        " + oldVersion + " -> " + releaseVersion);
        getLog().info("  Next version:   " + nextVersion);
        getLog().info("  Source branch:  " + branch);
        getLog().info("  Release branch: " + releaseBranch);
        getLog().info("  Tag:            v" + releaseVersion);
        getLog().info("  Project:        " + projectId);
        getLog().info("  Deploy site:    " + deploySite);
        getLog().info("  Skip verify:    " + skipVerify);
        getLog().info("  Dry run:        " + dryRun);
        getLog().info("");
        getLog().info("BUILD ENVIRONMENT");
        getLog().info("  Date:           " + Instant.now());
        getLog().info("  User:           " + System.getProperty("user.name", "unknown"));
        getLog().info("  Git commit:     " + gitCommit);
        getLog().info("  Git root:       " + gitRoot.getAbsolutePath());
        getLog().info("  Maven:          " + mavenVersion.lines().findFirst().orElse("unknown"));
        getLog().info("  Java version:   " + javaVersion);
        getLog().info("  OS:             " + System.getProperty("os.name") + " " +
                System.getProperty("os.arch"));
        getLog().info("");
    }
}
