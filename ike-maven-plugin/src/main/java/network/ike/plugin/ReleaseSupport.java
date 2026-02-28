package network.ike.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Shared utilities for release mojos.
 *
 * <p>All subprocess invocations use {@link ProcessBuilder} — no
 * library dependencies beyond the JDK and maven-plugin-api.
 */
class ReleaseSupport {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("<version>([^<]+)</version>");

    private ReleaseSupport() {}

    /**
     * Run a command, inherit IO so output streams to the Maven console.
     * Throws on non-zero exit code.
     */
    static void exec(File workDir, Log log, String... command)
            throws MojoExecutionException {
        log.info("» " + String.join(" ", command));
        try {
            int exit = new ProcessBuilder(command)
                    .directory(workDir)
                    .inheritIO()
                    .start()
                    .waitFor();
            if (exit != 0) {
                throw new MojoExecutionException(
                        "Command failed (exit " + exit + "): " +
                                String.join(" ", command));
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException(
                    "Failed to execute: " + String.join(" ", command), e);
        }
    }

    /**
     * Run a command and capture stdout as a trimmed String.
     * Throws on non-zero exit code.
     */
    static String execCapture(File workDir, String... command)
            throws MojoExecutionException {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workDir)
                    .redirectErrorStream(false)
                    .start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(),
                            StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n")).trim();
            }
            int exit = process.waitFor();
            if (exit != 0) {
                throw new MojoExecutionException(
                        "Command failed (exit " + exit + "): " +
                                String.join(" ", command));
            }
            return output;
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException(
                    "Failed to execute: " + String.join(" ", command), e);
        }
    }

    /**
     * Read the first {@code <version>} value from a POM file.
     */
    static String readPomVersion(File pomFile) throws MojoExecutionException {
        try {
            String content = Files.readString(pomFile.toPath(), StandardCharsets.UTF_8);
            Matcher matcher = VERSION_PATTERN.matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
            throw new MojoExecutionException(
                    "Could not extract <version> from " + pomFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read " + pomFile, e);
        }
    }

    /**
     * Replace the first occurrence of {@code <version>old</version>}
     * with {@code <version>new</version>} in the given POM file.
     */
    static void setPomVersion(File pomFile, String oldVersion, String newVersion)
            throws MojoExecutionException {
        try {
            String content = Files.readString(pomFile.toPath(), StandardCharsets.UTF_8);
            String oldTag = "<version>" + oldVersion + "</version>";
            String newTag = "<version>" + newVersion + "</version>";
            if (!content.contains(oldTag)) {
                throw new MojoExecutionException(
                        "POM does not contain " + oldTag);
            }
            // Replace first occurrence only
            String updated = content.replaceFirst(
                    Pattern.quote(oldTag), Matcher.quoteReplacement(newTag));
            Files.writeString(pomFile.toPath(), updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to update " + pomFile, e);
        }
    }

    /**
     * Resolve the Maven wrapper script from the git root.
     * Returns {@code mvnw} on Unix, {@code mvnw.cmd} on Windows.
     */
    static File resolveMavenWrapper(File gitRoot) throws MojoExecutionException {
        String name = System.getProperty("os.name", "")
                .toLowerCase().contains("win") ? "mvnw.cmd" : "mvnw";
        File wrapper = new File(gitRoot, name);
        if (!wrapper.exists()) {
            throw new MojoExecutionException(
                    "Maven wrapper not found: " + wrapper.getAbsolutePath() +
                            "\nMaven 4.x wrapper is required for this project.");
        }
        return wrapper;
    }

    /**
     * Get the git repository root directory.
     */
    static File gitRoot(File startDir) throws MojoExecutionException {
        String root = execCapture(startDir,
                "git", "rev-parse", "--show-toplevel");
        return new File(root);
    }

    /**
     * Assert that the git working tree is clean (no staged or unstaged changes).
     */
    static void requireCleanWorktree(File workDir) throws MojoExecutionException {
        try {
            execCapture(workDir, "git", "diff", "--quiet");
        } catch (MojoExecutionException _) {
            throw new MojoExecutionException(
                    "Working tree has unstaged changes. Commit or stash before proceeding.");
        }
        try {
            execCapture(workDir, "git", "diff", "--cached", "--quiet");
        } catch (MojoExecutionException _) {
            throw new MojoExecutionException(
                    "Working tree has staged changes. Commit or stash before proceeding.");
        }
    }

    /**
     * Get the current git branch name.
     */
    static String currentBranch(File workDir) throws MojoExecutionException {
        return execCapture(workDir, "git", "rev-parse", "--abbrev-ref", "HEAD");
    }

    /**
     * Derive the release version from a SNAPSHOT version.
     * {@code "2-SNAPSHOT"} becomes {@code "2"};
     * {@code "1.1.0-SNAPSHOT"} becomes {@code "1.1.0"}.
     */
    static String deriveReleaseVersion(String snapshotVersion) {
        return snapshotVersion.replace("-SNAPSHOT", "");
    }

    /**
     * Derive the next SNAPSHOT version by incrementing the last numeric
     * segment. {@code "2"} becomes {@code "3-SNAPSHOT"};
     * {@code "1.1.0"} becomes {@code "1.1.1-SNAPSHOT"}.
     */
    static String deriveNextSnapshot(String releaseVersion) {
        String base = releaseVersion.replace("-SNAPSHOT", "");
        int lastDot = base.lastIndexOf('.');
        if (lastDot >= 0) {
            String prefix = base.substring(0, lastDot + 1);
            String last = base.substring(lastDot + 1);
            return prefix + (Integer.parseInt(last) + 1) + "-SNAPSHOT";
        }
        // Simple integer version (e.g., "2" -> "3-SNAPSHOT")
        return (Integer.parseInt(base) + 1) + "-SNAPSHOT";
    }
}
