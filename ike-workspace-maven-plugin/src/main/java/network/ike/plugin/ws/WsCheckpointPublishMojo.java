package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;

/**
 * Execute a workspace checkpoint with auto-alignment.
 *
 * <p>This is the {@code -publish} counterpart of {@code ws:checkpoint}
 * (which defaults to a draft preview). Before checkpointing, this
 * goal automatically aligns inter-component dependency versions.
 *
 * <p>Usage: {@code mvn ws:checkpoint-apply}
 *
 * @see WsCheckpointDraftMojo
 */
@Mojo(name = "checkpoint-publish", requiresProject = false, threadSafe = true)
public class WsCheckpointPublishMojo extends WsCheckpointDraftMojo {

    /** Creates this goal instance. */
    public WsCheckpointPublishMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        publish = true;
        autoAlign();
        super.execute();
    }

    private void autoAlign() throws MojoExecutionException {
        File root = workspaceRoot();
        String mvn = WsReleaseDraftMojo.resolveMvnCommand(root);
        getLog().info("Auto-aligning workspace versions...");
        try {
            ReleaseSupport.exec(root, getLog(), mvn, "ws:align-apply", "-B");
        } catch (MojoExecutionException e) {
            getLog().warn("Auto-alignment completed with warnings: "
                    + e.getMessage());
        }
    }
}
