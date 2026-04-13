package network.ike.plugin.ws;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Displays available ws: workspace goals.
 *
 * @see <a href="https://github.com/IKE-Network/ike-pipeline">IKE Pipeline</a>
 */
@Mojo(name = "help", requiresProject = false, threadSafe = true)
public class WsHelpMojo extends AbstractMojo {

    /** Creates this goal instance. */
    public WsHelpMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("");
        getLog().info("IKE Workspace Tools — Available Goals");
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("");
        getLog().info("  ── Workspace Management ─────────────────────────────────");
        getLog().info("  ws:create                                       Create a new workspace (workspace.yaml + reactor POM)");
        getLog().info("  ws:add                                          Add a component to the workspace manifest");
        getLog().info("  ws:init                                         Clone/initialize repos from workspace.yaml");
        getLog().info("  ws:verify                                       Check manifest + VCS bridge state");
        getLog().info("  ws:verify-convergence                           Full verify + transitive dependency convergence");
        getLog().info("  ws:fix                                          Auto-fix issues found by verify");
        getLog().info("  ws:overview                                     Workspace overview (manifest, graph, status, cascade)");
        getLog().info("  ws:report                                       Generate workspace report");
        getLog().info("  ws:graph                                        Print dependency graph");
        getLog().info("  ws:stignore                                     Generate .stignore for Syncthing");
        getLog().info("  ws:sync                                         Sync workspace.yaml <-> actual branches");
        getLog().info("  ws:upgrade                                      Upgrade workspace plugin/reactor versions");
        getLog().info("  ws:pull                                         Git pull --rebase across repos");
        getLog().info("  ws:remove                                       Remove a component from the workspace");
        getLog().info("");
        getLog().info("  ── Feature Branching ────────────────────────────────────");
        getLog().info("  ws:feature-start-draft                          Preview feature branch creation feature branch across repos");
        getLog().info("  ws:feature-start-publish                        Create feature branch across repos");
        getLog().info("  ws:feature-abandon-draft                        Preview abandoning feature branch");
        getLog().info("  ws:feature-finish-merge-draft                   Preview no-ff merge");
        getLog().info("  ws:feature-finish-rebase-draft                  Preview rebase");
        getLog().info("  ws:feature-finish-squash-draft                  Preview squash-merge (default)");
        getLog().info("  ws:switch-draft                                Preview switching all components to a branch");
        getLog().info("  ws:switch-publish                              Switch all components to a branch");
        getLog().info("");
        getLog().info("  ── Release & Checkpoint ─────────────────────────────────");
        getLog().info("  ws:release-draft                                Preview workspace release components in topo order");
        getLog().info("  ws:checkpoint-draft                             Preview checkpoint (tag all repos, record YAML)");
        getLog().info("  ws:checkpoint-publish                           Execute checkpoint");
        getLog().info("  ws:post-release                                 Post-release cleanup (bump to next SNAPSHOT)");
        getLog().info("  ws:align-draft                                  Preview dependency alignment across workspace");
        getLog().info("  ws:release-notes                                Generate release notes from commits");
        getLog().info("");
        getLog().info("  ── VCS Bridge ───────────────────────────────────────────");
        getLog().info("  ws:sync                                         Reconcile git state after machine switch");
        getLog().info("  ws:commit                                       Catch-up + commit across repos");
        getLog().info("  ws:push                                         Catch-up + push across repos");
        getLog().info("  ws:check-branch                                 Warn on direct branching (git hook)");
        getLog().info("");
        getLog().info("  ── Branch Cleanup ───────────────────────────────────────");
        getLog().info("  ws:cleanup-draft                                List merged/stale feature branches");
        getLog().info("  ws:cleanup-publish                              Delete merged feature branches");
        getLog().info("");
        getLog().info("  ── Cascade ──────────────────────────────────────────────");
        getLog().info("  ws:cascade                                      Show downstream impact of a change");
        getLog().info("");
        getLog().info("  ws:help                                         This help message");
        getLog().info("");
        getLog().info("Options for workspace management:");
        getLog().info("  -Dworkspace.manifest=<path>  Path to workspace.yaml (auto-detected)");
        getLog().info("  -Dgroup=<name>               Restrict to group (status, init, pull)");
        getLog().info("  -Dcomponent=<name>           Component for ws:cascade (required)");
        getLog().info("  -Dformat=dot                 Graphviz DOT output for ws:graph");
        getLog().info("");
        getLog().info("Options for feature branching:");
        getLog().info("  -Dfeature=<name>             Feature name (branch: feature/<name>)");
        getLog().info("  -Dgroup=<name>               Restrict to group");
        getLog().info("  -DskipVersion=true           Skip POM version qualification (feature-start)");
        getLog().info("  -DtargetBranch=<name>        Merge target (default: main)");
        getLog().info("  -DkeepBranch=true            Keep branch after merge (feature-finish)");
        getLog().info("  -Dmessage=<msg>              Squash commit message (feature-finish-squash)");
        getLog().info("  -Dpublish=true               Execute (default is draft)");
        getLog().info("");
        getLog().info("Options for checkpoint:");
        getLog().info("  -Dname=<name>                Checkpoint name (auto-derived if omitted)");
        getLog().info("");
        getLog().info("Options for release:");
        getLog().info("  -Dcomponent=<name>           Release one specific component");
        getLog().info("  -Dgroup=<name>               Restrict to components in group");
        getLog().info("  -DdeploySite=true            Deploy site for each component");
        getLog().info("  -Dpublish=true               Execute release");
        getLog().info("  -Dpush=true                  Push releases to origin (default: true)");
        getLog().info("  -DskipCheckpoint=true        Skip pre-release checkpoint");
        getLog().info("");
        getLog().info("Options for VCS bridge:");
        getLog().info("  -Dmessage=<msg>              Commit message (ws:commit)");
        getLog().info("  -DaddAll=true                Stage all changes before commit");
        getLog().info("  -Dpush=true                  Push after commit");
        getLog().info("  -Dremote=<name>              Remote name (default: origin)");
        getLog().info("  -Dgroup=<name>               Restrict to group (commit, push)");
        getLog().info("");
        getLog().info("Options for ws:sync:");
        getLog().info("  -Dfrom=repos                 Update workspace.yaml from repos (default)");
        getLog().info("  -Dfrom=manifest              Switch repos to match workspace.yaml");
        getLog().info("  -Dpublish=true               Execute (default is draft)");
        getLog().info("");
    }
}
