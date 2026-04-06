package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;

/**
 * Execute a workspace checkpoint with auto-alignment.
 *
 * <p>This is the {@code -apply} counterpart of {@code ws:checkpoint}
 * (which defaults to a dry-run preview). Before checkpointing, this
 * goal automatically aligns inter-component dependency versions.
 *
 * <p>Usage: {@code mvn ws:checkpoint-apply}
 *
 * @see WsCheckpointMojo
 */
@Mojo(name = "checkpoint-apply", requiresProject = false, threadSafe = true)
public class WsCheckpointApplyMojo extends WsCheckpointMojo {

    @Override
    public void execute() throws MojoExecutionException {
        dryRun = false;
        autoAlign();
        super.execute();
    }

    private void autoAlign() throws MojoExecutionException {
        File root = workspaceRoot();
        String mvn = WsReleaseMojo.resolveMvnCommand(root);
        getLog().info("Auto-aligning workspace versions...");
        try {
            ReleaseSupport.exec(root, getLog(), mvn, "ws:align-apply", "-B");
        } catch (MojoExecutionException e) {
            getLog().warn("Auto-alignment completed with warnings: "
                    + e.getMessage());
        }
    }
}
