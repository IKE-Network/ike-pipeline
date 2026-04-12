package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;

import network.ike.workspace.Component;
import network.ike.workspace.ManifestWriter;
import network.ike.workspace.VersionSupport;
import network.ike.workspace.WorkspaceGraph;
import network.ike.plugin.ws.vcs.VcsOperations;
import network.ike.plugin.ws.vcs.VcsState;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Shared logic for feature-finish goals (squash, merge, rebase).
 *
 * <p>Each strategy goal delegates to this class for validation,
 * version stripping, workspace.yaml updates, branch deletion,
 * and state file writing. The actual merge operation is performed
 * by the strategy goal itself.
 */
class FeatureFinishSupport {

    private FeatureFinishSupport() {}

    /**
     * Detect the feature branch name from component branches.
     * If all components on a feature branch agree on the name,
     * returns it. Also checks the workspace root branch.
     *
     * @param root       workspace root directory
     * @param components component names to scan
     * @param mojo       the calling mojo (for gitBranch access)
     * @param log        Maven logger
     * @return the detected feature name (without "feature/" prefix)
     * @throws MojoExecutionException if no feature branch is detected
     */
    static String detectFeature(File root, List<String> components,
                                 AbstractWorkspaceMojo mojo, Log log)
            throws MojoExecutionException {
        Set<String> features = new TreeSet<>();

        // Check workspace root branch
        if (new File(root, ".git").exists()) {
            String wsBranch = mojo.gitBranch(root);
            if (wsBranch.startsWith("feature/")) {
                features.add(wsBranch.substring("feature/".length()));
            }
        }

        // Check component branches
        for (String name : components) {
            File dir = new File(root, name);
            if (!new File(dir, ".git").exists()) continue;
            String branch = mojo.gitBranch(dir);
            if (branch.startsWith("feature/")) {
                features.add(branch.substring("feature/".length()));
            }
        }

        if (features.isEmpty()) {
            throw new MojoExecutionException(
                    "No components are on a feature branch. "
                    + "Specify -Dfeature=<name> or switch to a feature branch.");
        }

        if (features.size() == 1) {
            String detected = features.iterator().next();
            log.info("  Detected feature: " + detected);
            return detected;
        }

        // Multiple features — list them for the user
        throw new MojoExecutionException(
                "Multiple feature branches detected: " + features
                + ". Specify -Dfeature=<name> to disambiguate.");
    }

    /**
     * Validate that a component is eligible for feature-finish.
     * Returns null if eligible, or a skip reason string.
     */
    static String validateComponent(File root, String name, String branchName,
                                     AbstractWorkspaceMojo mojo) {
        File dir = new File(root, name);
        File gitDir = new File(dir, ".git");

        if (!gitDir.exists()) {
            return "not cloned";
        }

        String currentBranch = mojo.gitBranch(dir);
        if (!currentBranch.equals(branchName)) {
            return "on " + currentBranch + ", not " + branchName;
        }

        String status = mojo.gitStatus(dir);
        if (!status.isEmpty()) {
            return "MODIFIED";  // Caller should throw
        }

        return null;
    }

    /**
     * Generate a structured commit message by aggregating per-component
     * commit history from the feature branch.
     */
    static String generateFeatureMessage(File root, List<String> components,
                                          String branchName, String targetBranch,
                                          String userMessage, Log log) {
        var sb = new StringBuilder();
        if (userMessage != null && !userMessage.isBlank()) {
            sb.append(userMessage).append("\n\n");
        }
        sb.append(branchName).append("\n");

        for (String name : components) {
            File dir = new File(root, name);
            try {
                List<String> commits = VcsOperations.commitLog(
                        dir, targetBranch, branchName);
                if (commits.isEmpty()) continue;
                sb.append("\n## ").append(name)
                  .append(" (").append(commits.size()).append(" commit")
                  .append(commits.size() == 1 ? "" : "s").append(")\n");
                for (String line : commits) {
                    String msg = line.contains(" ")
                            ? line.substring(line.indexOf(' ') + 1) : line;
                    sb.append("- ").append(msg).append("\n");
                }
            } catch (MojoExecutionException e) {
                log.debug("Could not get log for " + name + ": " + e.getMessage());
            }
        }

        // Workspace repo changes
        try {
            List<String> wsCommits = VcsOperations.commitLog(
                    root, targetBranch, branchName);
            if (!wsCommits.isEmpty()) {
                sb.append("\n## workspace (").append(wsCommits.size())
                  .append(" commit").append(wsCommits.size() == 1 ? "" : "s")
                  .append(")\n");
                for (String line : wsCommits) {
                    String msg = line.contains(" ")
                            ? line.substring(line.indexOf(' ') + 1) : line;
                    sb.append("- ").append(msg).append("\n");
                }
            }
        } catch (MojoExecutionException e) {
            log.debug("Could not get workspace log: " + e.getMessage());
        }

        return sb.toString().stripTrailing();
    }

    /**
     * Strip branch-qualified version back to base SNAPSHOT.
     * Returns the base version, or null if no stripping was needed.
     */
    static String stripBranchVersion(File dir, Component component, Log log)
            throws MojoExecutionException {
        // Read actual version from POM on disk — workspace.yaml may be stale
        // if the branch update commit failed (#83).
        String currentVersion = readCurrentVersion(dir, log);
        if (currentVersion == null || !VersionSupport.isBranchQualified(currentVersion)) {
            return null;
        }

        String baseVersion = VersionSupport.extractNumericBase(
                VersionSupport.stripSnapshot(currentVersion)) + "-SNAPSHOT";

        log.info("    version: " + currentVersion + " → " + baseVersion);
        setAllVersions(dir, currentVersion, baseVersion, log);

        // Also strip any other branch-qualified versions in the POM tree
        // (BOM imports, version properties, etc. set by cascadeBomProperties
        // and cascadeBomImports during feature-start).
        stripAllBranchQualifiedVersions(dir, log);

        ReleaseSupport.exec(dir, log, "git", "add", "-A");
        ReleaseSupport.exec(dir, log, "git", "commit", "-m",
                "merge-prep: strip branch qualifier → " + baseVersion);

        return baseVersion;
    }

    /**
     * Strip branch-qualified version in bare mode.
     */
    static String stripBranchVersionBare(File dir, Log log)
            throws MojoExecutionException {
        File pom = new File(dir, "pom.xml");
        if (!pom.exists()) return null;

        String currentVersion;
        try {
            currentVersion = ReleaseSupport.readPomVersion(pom);
        } catch (MojoExecutionException e) {
            return null;
        }

        if (currentVersion == null || !VersionSupport.isBranchQualified(currentVersion)) {
            return null;
        }

        String baseVersion = VersionSupport.extractNumericBase(
                VersionSupport.stripSnapshot(currentVersion)) + "-SNAPSHOT";

        log.info("  Version: " + currentVersion + " → " + baseVersion);
        setAllVersions(dir, currentVersion, baseVersion, log);
        stripAllBranchQualifiedVersions(dir, log);
        ReleaseSupport.exec(dir, log, "git", "add", "-A");
        ReleaseSupport.exec(dir, log, "git", "commit", "-m",
                "merge-prep: strip branch qualifier → " + baseVersion);

        return baseVersion;
    }

    /**
     * Delete feature branch locally and remotely.
     */
    static void deleteBranch(File dir, Log log, String branchName)
            throws MojoExecutionException {
        VcsOperations.deleteBranch(dir, log, branchName);
        log.info("    deleted local branch: " + branchName);

        Optional<String> remoteSha = VcsOperations.remoteSha(dir, "origin", branchName);
        if (remoteSha.isPresent()) {
            VcsOperations.deleteRemoteBranch(dir, log, "origin", branchName);
            log.info("    deleted remote branch: origin/" + branchName);
        } else {
            log.info("    remote branch origin/" + branchName
                    + " does not exist (never pushed) — skipping");
        }
    }

    /**
     * Clean up feature branch snapshot sites.
     */
    static void cleanFeatureSites(File root, List<String> components,
                                    String branchName, Log log) {
        String featurePath = ReleaseSupport.branchToSitePath(branchName);
        for (String name : components) {
            String siteDisk = ReleaseSupport.siteDiskPath(
                    name, "snapshot", featurePath);
            try {
                ReleaseSupport.cleanRemoteSiteDir(
                        new File(root, name), log, siteDisk);
            } catch (MojoExecutionException e) {
                log.debug("No snapshot site to clean for " + name
                        + ": " + e.getMessage());
            }
        }
    }

    /**
     * Update workspace.yaml branch fields back to targetBranch and commit.
     */
    static void updateWorkspaceYaml(Path manifestPath, List<String> components,
                                      String targetBranch, String feature,
                                      Log log) {
        try {
            Map<String, String> updates = new LinkedHashMap<>();
            for (String name : components) {
                updates.put(name, targetBranch);
            }
            ManifestWriter.updateBranches(manifestPath, updates);
            log.info("  Updated workspace.yaml branches → " + targetBranch);

            File wsRoot = manifestPath.getParent().toFile();
            File wsGit = new File(wsRoot, ".git");
            if (wsGit.exists()) {
                ReleaseSupport.exec(wsRoot, log, "git", "add", "workspace.yaml");
                if (VcsOperations.hasStagedChanges(wsRoot)) {
                    ReleaseSupport.exec(wsRoot, log, "git", "commit", "-m",
                            "workspace: restore branches to " + targetBranch
                                    + " after feature/" + feature);
                }
            }
        } catch (IOException | MojoExecutionException e) {
            log.warn("  Could not update workspace.yaml: " + e.getMessage());
        }
    }

    /**
     * Merge the workspace aggregator repo from the feature branch to the
     * target branch. Mirrors the per-component merge: checkout target,
     * no-ff merge, push.
     */
    static void mergeWorkspaceRepo(Path manifestPath, String branchName,
                                     String targetBranch, boolean keepBranch,
                                     Log log)
            throws MojoExecutionException {
        File wsRoot = manifestPath.getParent().toFile();
        if (!new File(wsRoot, ".git").exists()) return;

        String wsBranch = null;
        try {
            wsBranch = VcsOperations.currentBranch(wsRoot);
        } catch (MojoExecutionException e) {
            return;
        }

        if (wsBranch != null && wsBranch.equals(branchName)) {
            log.info("  Merging workspace repo: " + branchName + " → " + targetBranch);
            VcsOperations.checkout(wsRoot, log, targetBranch);
            VcsOperations.mergeNoFf(wsRoot, log, branchName,
                    "Merge " + branchName + " into " + targetBranch);
            VcsOperations.pushIfRemoteExists(wsRoot, log, "origin", targetBranch);
        }

        if (!keepBranch) {
            try {
                deleteBranch(wsRoot, log, branchName);
            } catch (MojoExecutionException e) {
                log.warn("  Could not delete ws branch: " + e.getMessage());
            }
        }

        // Write state file for ws
        if (VcsState.isIkeManaged(wsRoot.toPath())) {
            VcsOperations.writeVcsState(wsRoot, VcsState.ACTION_FEATURE_FINISH);
        }
    }

    // ── Internal helpers ─────────────────────────────────────────

    private static String readCurrentVersion(File dir, Log log) {
        try {
            return ReleaseSupport.readPomVersion(new File(dir, "pom.xml"));
        } catch (MojoExecutionException e) {
            log.warn("    Could not read version from " + dir.getName()
                    + "/pom.xml: " + e.getMessage());
            return null;
        }
    }

    /**
     * Scan all POM files in a component for any branch-qualified version
     * strings and strip them back to base SNAPSHOT. This reverses the
     * cascade done by feature-start (BOM properties, BOM imports,
     * version properties).
     *
     * <p>Uses a regex to find version strings matching the pattern
     * {@code X.Y.Z-branch-qualifier-SNAPSHOT} and replaces them with
     * {@code X.Y.Z-SNAPSHOT}.
     */
    private static void stripAllBranchQualifiedVersions(File dir, Log log)
            throws MojoExecutionException {
        List<File> allPoms = ReleaseSupport.findPomFiles(dir);
        // Pattern: digits.digits.digits-word-chars-SNAPSHOT
        java.util.regex.Pattern branchVersionPattern = java.util.regex.Pattern.compile(
                "(\\d+\\.\\d+\\.\\d+)-[a-zA-Z][\\w.-]+-SNAPSHOT");

        for (File pom : allPoms) {
            try {
                String content = Files.readString(pom.toPath(), StandardCharsets.UTF_8);
                java.util.regex.Matcher m = branchVersionPattern.matcher(content);
                String updated = m.replaceAll("$1-SNAPSHOT");
                if (!updated.equals(content)) {
                    Files.writeString(pom.toPath(), updated, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                log.warn("    Could not strip versions in " + pom + ": "
                        + e.getMessage());
            }
        }
    }

    static void setAllVersions(File dir, String oldVersion, String newVersion,
                                 Log log) throws MojoExecutionException {
        File pom = new File(dir, "pom.xml");
        ReleaseSupport.setPomVersion(pom, oldVersion, newVersion);

        List<File> allPoms = ReleaseSupport.findPomFiles(dir);
        for (File subPom : allPoms) {
            if (subPom.equals(pom)) continue;
            try {
                String content = Files.readString(subPom.toPath(), StandardCharsets.UTF_8);
                if (content.contains("<version>" + oldVersion + "</version>")) {
                    String updated = content.replace(
                            "<version>" + oldVersion + "</version>",
                            "<version>" + newVersion + "</version>");
                    Files.writeString(subPom.toPath(), updated, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                log.warn("    Could not update " + subPom + ": " + e.getMessage());
            }
        }
    }
}
