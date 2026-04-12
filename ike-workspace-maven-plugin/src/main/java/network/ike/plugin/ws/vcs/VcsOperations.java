package network.ike.plugin.ws.vcs;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Git and VCS state operations for the IKE VCS Bridge.
 *
 * <p>All git commands use {@link ProcessBuilder}. Commands that modify
 * state (commit, push, branch creation) set {@code IKE_VCS_CONTEXT}
 * in the subprocess environment so that the pre-commit and pre-push
 * hooks allow the operation through.
 */
public class VcsOperations {

    private static final String IKE_VCS_CONTEXT = "IKE_VCS_CONTEXT";
    private static final String CONTEXT_VALUE = "ike-maven-plugin";

    private VcsOperations() {}

    // ── Git queries ──────────────────────────────────────────────

    /**
     * Get the 8-character short SHA of HEAD.
     *
     * @param dir the repository root directory
     * @return the short SHA string
     * @throws MojoExecutionException if the git command fails
     */
    public static String headSha(File dir) throws MojoExecutionException {
        return capture(dir, "git", "rev-parse", "--short=8", "HEAD");
    }

    /**
     * Get the current branch name.
     *
     * @param dir the repository root directory
     * @return the current branch name
     * @throws MojoExecutionException if the git command fails
     */
    public static String currentBranch(File dir) throws MojoExecutionException {
        return capture(dir, "git", "branch", "--show-current");
    }

    /**
     * Get the 8-character short SHA of a remote branch, or empty if unreachable.
     *
     * @param dir    the repository root directory
     * @param remote the remote name (e.g., "origin")
     * @param branch the branch name to query
     * @return the short SHA, or empty if the remote branch is unreachable
     * @throws MojoExecutionException if the git command fails
     */
    public static Optional<String> remoteSha(File dir, String remote, String branch)
            throws MojoExecutionException {
        try {
            String output = capture(dir, "git", "ls-remote", remote, branch);
            if (output.isEmpty()) {
                return Optional.empty();
            }
            // ls-remote output: <full-sha>\trefs/heads/<branch>
            String fullSha = output.split("\\s+")[0];
            return Optional.of(fullSha.substring(0, 8));
        } catch (MojoExecutionException e) {
            return Optional.empty();
        }
    }

    /**
     * Check whether the working tree is clean (no staged or unstaged changes).
     *
     * @param dir the repository root directory
     * @return true if the working tree has no changes
     */
    public static boolean isClean(File dir) {
        try {
            String status = capture(dir, "git", "status", "--porcelain");
            return status.isEmpty();
        } catch (MojoExecutionException e) {
            return false;
        }
    }

    /**
     * Check whether there are staged changes ready to commit.
     *
     * @param dir the repository root directory
     * @return true if the index has staged changes
     */
    public static boolean hasStagedChanges(File dir) {
        try {
            String diff = capture(dir, "git", "diff", "--cached", "--name-only");
            return !diff.isEmpty();
        } catch (MojoExecutionException e) {
            return false;
        }
    }

    /**
     * Get the one-line commit log between two refs.
     */
    public static List<String> commitLog(File dir, String base, String head)
            throws MojoExecutionException {
        String output = capture(dir, "git", "log",
                base + ".." + head, "--oneline", "--no-decorate");
        if (output.isEmpty()) return List.of();
        return List.of(output.split("\n"));
    }

    // ── Git operations ───────────────────────────────────────────

    /**
     * Fetch from all remotes.
     *
     * @param dir the repository root directory
     * @param log Maven logger
     * @throws MojoExecutionException if the git command fails
     */
    public static void fetch(File dir, Log log) throws MojoExecutionException {
        run(dir, log, null, "git", "fetch", "--all", "--quiet");
    }

    /**
     * Soft reset (no --hard) — updates HEAD and index, leaves working tree.
     *
     * @param dir the repository root directory
     * @param log Maven logger
     * @param ref the ref to reset to (e.g., "origin/main")
     * @throws MojoExecutionException if the git command fails
     */
    public static void resetSoft(File dir, Log log, String ref)
            throws MojoExecutionException {
        run(dir, log, null, "git", "reset", ref, "--quiet");
    }

    /**
     * Checkout an existing branch.
     *
     * @param dir    the repository root directory
     * @param log    Maven logger
     * @param branch the branch to check out
     * @throws MojoExecutionException if the git command fails
     */
    public static void checkout(File dir, Log log, String branch)
            throws MojoExecutionException {
        run(dir, log, null, "git", "checkout", branch);
    }

    /**
     * Create and checkout a new branch.
     *
     * @param dir    the repository root directory
     * @param log    Maven logger
     * @param branch the new branch name to create
     * @throws MojoExecutionException if the git command fails
     */
    public static void checkoutNew(File dir, Log log, String branch)
            throws MojoExecutionException {
        run(dir, log, null, "git", "checkout", "-b", branch);
    }

    /**
     * Stage all changes and commit with the given message.
     * Sets {@code IKE_VCS_CONTEXT} to bypass the pre-commit hook.
     *
     * @param dir     the repository root directory
     * @param log     Maven logger
     * @param message the commit message
     * @throws MojoExecutionException if the git command fails
     */
    public static void commit(File dir, Log log, String message)
            throws MojoExecutionException {
        runWithContext(dir, log, "git", "commit", "-m", message);
    }

    /**
     * Commit without staging (assumes files are already staged).
     * Sets {@code IKE_VCS_CONTEXT} to bypass the pre-commit hook.
     *
     * @param dir     the repository root directory
     * @param log     Maven logger
     * @param message the commit message, or {@code null} to open the editor
     * @throws MojoExecutionException if the git command fails
     */
    public static void commitStaged(File dir, Log log, String message)
            throws MojoExecutionException {
        runWithContext(dir, log, "git", "commit", "-m", message);
    }

    /**
     * Stage all files.
     *
     * @param dir the repository root directory
     * @param log Maven logger
     * @throws MojoExecutionException if the git command fails
     */
    public static void addAll(File dir, Log log) throws MojoExecutionException {
        run(dir, log, null, "git", "add", "-A");
    }

    /**
     * Push to remote. Sets {@code IKE_VCS_CONTEXT} to bypass the pre-push hook.
     *
     * @param dir    the repository root directory
     * @param log    Maven logger
     * @param remote the remote name (e.g., "origin")
     * @param branch the branch to push
     * @throws MojoExecutionException if the git command fails
     */
    public static void push(File dir, Log log, String remote, String branch)
            throws MojoExecutionException {
        runWithContext(dir, log, "git", "push", remote, branch);
    }

    /**
     * Push to remote, ignoring failures (no remote, offline, etc.).
     * Logs a warning on failure instead of throwing.
     *
     * @param dir    the repository root directory
     * @param log    Maven logger
     * @param remote the remote name (e.g., "origin")
     * @param branch the branch to push
     */
    public static void pushSafe(File dir, Log log, String remote, String branch) {
        try {
            push(dir, log, remote, branch);
        } catch (MojoExecutionException e) {
            log.warn("  Push failed (non-fatal): " + e.getMessage());
        }
    }

    /**
     * Push to remote if it exists. If no remote is configured, logs
     * a helpful message instead of failing with a cryptic git error.
     *
     * @param dir    the repository root directory
     * @param log    Maven logger
     * @param remote the remote name (e.g., "origin")
     * @param branch the branch to push
     */
    public static void pushIfRemoteExists(File dir, Log log,
                                            String remote, String branch) {
        try {
            if (!network.ike.plugin.ReleaseSupport.hasRemote(dir, remote)) {
                log.info("  No remote '" + remote + "' configured for "
                        + dir.getName() + " — changes remain local.");
                return;
            }
            push(dir, log, remote, branch);
        } catch (MojoExecutionException e) {
            log.warn("  Push failed (non-fatal): " + e.getMessage());
        }
    }

    /**
     * Push to remote with upstream tracking.
     * Sets {@code IKE_VCS_CONTEXT} to bypass the pre-push hook.
     *
     * @param dir    the repository root directory
     * @param log    Maven logger
     * @param remote the remote name (e.g., "origin")
     * @param branch the branch to push
     * @throws MojoExecutionException if the git command fails
     */
    public static void pushWithUpstream(File dir, Log log, String remote, String branch)
            throws MojoExecutionException {
        runWithContext(dir, log, "git", "push", "-u", remote, branch);
    }

    /**
     * Delete a local branch. Uses {@code -D} (force) because squash-merged
     * branches are not recognized as "fully merged" by git.
     *
     * @param dir    the repository root directory
     * @param log    Maven logger
     * @param branch the branch to delete
     * @throws MojoExecutionException if the git command fails
     */
    public static void deleteBranch(File dir, Log log, String branch)
            throws MojoExecutionException {
        run(dir, log, null, "git", "branch", "-D", branch);
    }

    /**
     * Delete a remote branch.
     *
     * @param dir    the repository root directory
     * @param log    Maven logger
     * @param remote the remote name (e.g., "origin")
     * @param branch the branch to delete on the remote
     * @throws MojoExecutionException if the git command fails
     */
    public static void deleteRemoteBranch(File dir, Log log, String remote, String branch)
            throws MojoExecutionException {
        runWithContext(dir, log, "git", "push", remote, "--delete", branch);
    }

    /**
     * Squash-merge a branch into the current branch (does not commit).
     *
     * @param dir    the repository root directory
     * @param log    Maven logger
     * @param branch the branch to squash-merge
     * @throws MojoExecutionException if the git command fails
     */
    public static void mergeSquash(File dir, Log log, String branch)
            throws MojoExecutionException {
        run(dir, log, null, "git", "merge", "--squash", branch);
    }

    /**
     * No-fast-forward merge with a merge commit.
     *
     * @param dir     the repository root directory
     * @param log     Maven logger
     * @param branch  the branch to merge
     * @param message the merge commit message
     * @throws MojoExecutionException if the git command fails
     */
    public static void mergeNoFf(File dir, Log log, String branch, String message)
            throws MojoExecutionException {
        runWithContext(dir, log, "git", "merge", "--no-ff", branch, "-m", message);
    }

    /**
     * Rebase current branch onto the given base.
     *
     * @param dir  the repository root directory
     * @param log  Maven logger
     * @param onto the branch or ref to rebase onto
     * @throws MojoExecutionException if the git command fails
     */
    public static void rebase(File dir, Log log, String onto)
            throws MojoExecutionException {
        run(dir, log, null, "git", "rebase", onto);
    }

    /**
     * Fast-forward-only merge.
     *
     * @param dir    the repository root directory
     * @param log    Maven logger
     * @param branch the branch to merge
     * @throws MojoExecutionException if the git command fails
     */
    public static void mergeFfOnly(File dir, Log log, String branch)
            throws MojoExecutionException {
        run(dir, log, null, "git", "merge", "--ff-only", branch);
    }

    /**
     * Check whether a local branch exists.
     *
     * @param dir    the repository root directory
     * @param branch the branch name to check
     * @return true if the branch exists locally
     */
    public static boolean localBranchExists(File dir, String branch) {
        try {
            String output = capture(dir, "git", "branch", "--list", branch);
            return !output.trim().isEmpty();
        } catch (MojoExecutionException e) {
            return false;
        }
    }

    /**
     * List local branches matching a prefix that are fully merged into
     * the given target branch.
     *
     * @param dir    the repository root directory
     * @param target the branch to check merge status against (e.g., "main")
     * @param prefix the branch name prefix to filter (e.g., "feature/")
     * @return list of merged branch names (trimmed, without leading {@code * })
     */
    public static List<String> mergedBranches(File dir, String target, String prefix) {
        try {
            String output = capture(dir, "git", "branch", "--merged", target);
            if (output.isEmpty()) return List.of();
            return output.lines()
                    .map(line -> line.replaceFirst("^[* ] +", ""))
                    .filter(b -> b.startsWith(prefix))
                    .filter(b -> !b.equals(target))
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of();
        }
    }

    /**
     * List all local branches matching a prefix.
     *
     * @param dir    the repository root directory
     * @param prefix the branch name prefix to filter (e.g., "feature/")
     * @return list of branch names
     */
    public static List<String> localBranches(File dir, String prefix) {
        try {
            String output = capture(dir, "git", "branch");
            if (output.isEmpty()) return List.of();
            return output.lines()
                    .map(line -> line.replaceFirst("^[* ] +", ""))
                    .filter(b -> b.startsWith(prefix))
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of();
        }
    }

    /**
     * Get the date of the last commit on a branch (ISO format).
     *
     * @param dir    the repository root directory
     * @param branch the branch name
     * @return the commit date string, or "unknown" on failure
     */
    public static String branchLastCommitDate(File dir, String branch) {
        try {
            return capture(dir, "git", "log", "-1", "--format=%ci", branch);
        } catch (MojoExecutionException e) {
            return "unknown";
        }
    }

    // ── VCS state operations ─────────────────────────────────────

    /**
     * Write the VCS state file for the given directory.
     *
     * @param dir    the repository root directory
     * @param action the action constant (e.g., {@link VcsState#ACTION_COMMIT})
     * @throws MojoExecutionException if writing the state file fails
     */
    public static void writeVcsState(File dir, String action)
            throws MojoExecutionException {
        try {
            String branch = currentBranch(dir);
            String sha = headSha(dir);
            VcsState state = VcsState.create(branch, sha, action);
            VcsState.writeTo(dir.toPath(), state);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failed to write VCS state file: " + e.getMessage(), e);
        }
    }

    /**
     * Check whether the local HEAD matches the VCS state file.
     *
     * @param dir the repository root directory
     * @return true if in sync or if no state file exists, false if catch-up is needed
     * @throws MojoExecutionException if reading git state fails
     */
    public static boolean needsSync(File dir) throws MojoExecutionException {
        Optional<VcsState> state = VcsState.readFrom(dir.toPath());
        if (state.isEmpty()) {
            return false;
        }
        String localSha = headSha(dir);
        String localBranch = currentBranch(dir);
        VcsState s = state.get();
        return !s.sha().equals(localSha) || !s.branch().equals(localBranch);
    }

    /**
     * Synchronize local git state to match the VCS state file.
     * Fetches from all remotes, switches branch if needed, and soft-resets.
     *
     * @param dir the repository root directory
     * @param log Maven logger
     * @return the resulting HEAD SHA after sync
     * @throws MojoExecutionException if a git command or state file read fails
     */
    public static String sync(File dir, Log log) throws MojoExecutionException {
        Optional<VcsState> stateOpt = VcsState.readFrom(dir.toPath());
        if (stateOpt.isEmpty()) {
            log.info("  No VCS state file — nothing to sync.");
            return headSha(dir);
        }

        VcsState state = stateOpt.get();
        log.info("  Syncing to: " + state.action() + " by " + state.machine()
                + " at " + state.timestamp());

        fetch(dir, log);

        String localBranch = currentBranch(dir);
        if (!state.branch().equals(localBranch)) {
            if (!localBranchExists(dir, state.branch())) {
                // Branch doesn't exist locally — check remote
                Optional<String> remoteCheck = remoteSha(dir, "origin", state.branch());
                if (remoteCheck.isEmpty()) {
                    log.warn("  Branch " + state.branch()
                            + " does not exist locally or on origin.");
                    log.warn("  The branch may not have been pushed from "
                            + state.machine() + " yet.");
                    log.warn("  Push from " + state.machine()
                            + " first, then retry sync.");
                    return headSha(dir);
                }
                // Create local tracking branch from remote
                log.info("  Creating local branch from origin: " + state.branch());
                run(dir, log, null, "git", "checkout", "-b",
                        state.branch(), "origin/" + state.branch());
            } else {
                log.info("  Switching branch: " + localBranch + " → " + state.branch());
                checkout(dir, log, state.branch());
            }
        }

        Optional<String> remoteRef = remoteSha(dir, "origin", state.branch());
        if (remoteRef.isPresent()) {
            resetSoft(dir, log, "origin/" + state.branch());
        } else {
            log.info("  No remote ref for " + state.branch()
                    + " on origin — branch is local-only, using local state.");
        }

        String newSha = headSha(dir);
        if (!newSha.equals(state.sha())) {
            log.warn("  HEAD after sync (" + newSha + ") does not match state file ("
                    + state.sha() + ").");
            log.warn("  The push from " + state.machine() + " may not have completed.");
            log.warn("  Push from " + state.machine() + " first, then retry sync.");
        } else {
            log.info("  HEAD now matches state file: " + newSha);
        }

        return newSha;
    }

    /**
     * Catch-up preamble: sync if needed, otherwise report that we're current.
     * Used by all goals that modify state (commit, push, feature-start, etc.).
     *
     * @param dir the repository root directory
     * @param log Maven logger
     * @throws MojoExecutionException if sync fails
     */
    public static void catchUp(File dir, Log log) throws MojoExecutionException {
        if (!VcsState.isIkeManaged(dir.toPath())) {
            return;
        }
        if (needsSync(dir)) {
            log.info("  VCS state is behind — catching up...");
            sync(dir, log);
        }
    }

    // ── Internal helpers ─────────────────────────────────────────

    /**
     * Run a command with output routed through the Maven logger.
     * Optionally sets environment variables.
     */
    private static void run(File workDir, Log log, Map<String, String> env,
                            String... command) throws MojoExecutionException {
        log.debug("» " + String.join(" ", command));
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(workDir)
                    .redirectErrorStream(true);
            if (env != null) {
                pb.environment().putAll(env);
            }
            Process proc = pb.start();
            try (var reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("  " + line);
                }
            }
            int exit = proc.waitFor();
            if (exit != 0) {
                throw new MojoExecutionException(
                        "Command failed (exit " + exit + "): "
                                + String.join(" ", command));
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException(
                    "Failed to execute: " + String.join(" ", command), e);
        }
    }

    /**
     * Run a command with {@code IKE_VCS_CONTEXT} set in the environment.
     */
    private static void runWithContext(File workDir, Log log, String... command)
            throws MojoExecutionException {
        run(workDir, log, Map.of(IKE_VCS_CONTEXT, CONTEXT_VALUE), command);
    }

    /**
     * Run a command and capture stdout as a trimmed string.
     */
    private static String capture(File workDir, String... command)
            throws MojoExecutionException {
        try {
            Process proc = new ProcessBuilder(command)
                    .directory(workDir)
                    .redirectErrorStream(false)
                    .start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(),
                            StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n")).trim();
            }
            int exit = proc.waitFor();
            if (exit != 0) {
                throw new MojoExecutionException(
                        "Command failed (exit " + exit + "): "
                                + String.join(" ", command));
            }
            return output;
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException(
                    "Failed to execute: " + String.join(" ", command), e);
        }
    }
}
