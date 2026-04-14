package network.ike.plugin.ws;

import network.ike.plugin.ws.vcs.VcsOperations;
import network.ike.plugin.ws.vcs.VcsState;
import network.ike.workspace.WorkspaceGraph;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Commit with a VCS bridge catch-up preamble.
 *
 * <p>When run from a workspace root (where {@code workspace.yaml} exists),
 * iterates all component repositories in topological order, staging and
 * committing changes in each. When run from a single repository, operates
 * on the current directory only.
 *
 * <p>Usage:
 * <pre>{@code
 * mvn ws:commit -Dmessage="my commit message" -DaddAll=true
 * mvn ws:commit -Dmessage="fix" -DaddAll=true -Dgroup=core
 * }</pre>
 */
@Mojo(name = "commit", projectRequired = false)
public class CommitMojo extends AbstractWorkspaceMojo {

    /** Creates this goal instance. */
    public CommitMojo() {}

    /**
     * Commit message. If omitted, git opens the editor and the
     * prepare-commit-msg hook generates a message via Claude.
     */
    @Parameter(property = "message")
    String message;

    /**
     * Stage all changes before committing ({@code git add -A}).
     */
    @Parameter(property = "addAll", defaultValue = "false")
    boolean addAll;

    /**
     * Push to origin after committing.
     */
    @Parameter(property = "push", defaultValue = "false")
    boolean push;

    /**
     * Restrict to a named group (or single component). Default: all.
     */
    @Parameter(property = "group")
    String group;

    @Override
    public void execute() throws MojoException {
        if (isWorkspaceMode()) {
            executeWorkspace();
        } else {
            executeSingleRepo(new File(System.getProperty("user.dir")));
        }
    }

    private void executeWorkspace() throws MojoException {
        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();

        Set<String> targets;
        if (group != null && !group.isEmpty()) {
            targets = graph.expandGroup(group);
        } else {
            targets = graph.manifest().components().keySet();
        }

        List<String> sorted = graph.topologicalSort(new LinkedHashSet<>(targets));

        getLog().info("");
        getLog().info(header("Commit"));
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("");

        int committed = 0;
        int skipped = 0;
        int failed = 0;

        // Include workspace root in commit scan (#102)
        if (new File(root, ".git").exists()) {
            try {
                VcsOperations.catchUp(root, getLog());
                if (addAll) {
                    VcsOperations.addAll(root, getLog());
                }
                if (VcsOperations.hasStagedChanges(root)) {
                    if (message != null && !message.isBlank()) {
                        VcsOperations.commit(root, getLog(), message);
                    } else {
                        VcsOperations.commitStaged(root, getLog(), null);
                    }
                    VcsOperations.writeVcsState(root, VcsState.Action.COMMIT);
                    if (push) {
                        String branch = VcsOperations.currentBranch(root);
                        VcsOperations.push(root, getLog(), "origin", branch);
                        VcsOperations.writeVcsState(root, VcsState.Action.PUSH);
                    }
                    getLog().info(Ansi.green("  ✓ ") + "workspace root");
                    committed++;
                } else if (!VcsOperations.isClean(root)) {
                    getLog().debug("workspace root — unstaged changes, skipping");
                    skipped++;
                } else {
                    getLog().debug("workspace root — clean, skipping");
                    skipped++;
                }
            } catch (MojoException e) {
                getLog().warn(Ansi.red("  ✗ ") + "workspace root — " + e.getMessage());
                failed++;
            }
        }

        for (String name : sorted) {
            File dir = new File(root, name);
            File gitDir = new File(dir, ".git");

            if (!gitDir.exists()) {
                getLog().debug(name + " — not cloned, skipping");
                skipped++;
                continue;
            }

            try {
                VcsOperations.catchUp(dir, getLog());

                if (addAll) {
                    VcsOperations.addAll(dir, getLog());
                }

                if (!VcsOperations.hasStagedChanges(dir) && VcsOperations.isClean(dir)) {
                    getLog().debug(name + " — clean, skipping");
                    skipped++;
                    continue;
                }

                if (!VcsOperations.hasStagedChanges(dir)) {
                    getLog().debug(name + " — no staged changes, skipping");
                    skipped++;
                    continue;
                }

                if (message != null && !message.isBlank()) {
                    VcsOperations.commit(dir, getLog(), message);
                } else {
                    VcsOperations.commitStaged(dir, getLog(), null);
                }

                VcsOperations.writeVcsState(dir, VcsState.Action.COMMIT);

                if (push) {
                    String branch = VcsOperations.currentBranch(dir);
                    VcsOperations.push(dir, getLog(), "origin", branch);
                    VcsOperations.writeVcsState(dir, VcsState.Action.PUSH);
                }

                getLog().info(Ansi.green("  ✓ ") + name);
                committed++;
            } catch (MojoException e) {
                getLog().warn(Ansi.red("  ✗ ") + name + " — " + e.getMessage());
                failed++;
            }
        }

        getLog().info("");
        getLog().info("  Done: " + committed + " committed, " + skipped
                + " skipped, " + failed + " failed");
        getLog().info("");

        if (failed > 0) {
            getLog().warn("  Some commits failed — check output above for details.");
        }

        writeReport("ws:commit", committed + " committed, " + skipped
                + " skipped, " + failed + " failed.\n");
    }

    private void executeSingleRepo(File dir) throws MojoException {
        getLog().info("");
        getLog().info("IKE VCS Bridge — Commit");
        getLog().info("══════════════════════════════════════════════════════════════");

        VcsOperations.catchUp(dir, getLog());

        if (addAll) {
            getLog().info("  Staging all changes...");
            VcsOperations.addAll(dir, getLog());
        }

        if (message != null && !message.isBlank()) {
            getLog().info("  Committing...");
            VcsOperations.commit(dir, getLog(), message);
        } else {
            getLog().info("  Committing (editor will open for message)...");
            VcsOperations.commitStaged(dir, getLog(), null);
        }

        VcsOperations.writeVcsState(dir, VcsState.Action.COMMIT);

        if (push) {
            String branch = VcsOperations.currentBranch(dir);
            getLog().info("  Pushing to origin/" + branch + "...");
            VcsOperations.push(dir, getLog(), "origin", branch);
            VcsOperations.writeVcsState(dir, VcsState.Action.PUSH);
        }

        getLog().info("");
        getLog().info("  Done.");
        getLog().info("");
    }
}
