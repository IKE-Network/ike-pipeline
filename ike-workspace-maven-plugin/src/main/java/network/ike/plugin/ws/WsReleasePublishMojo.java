package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;

/**
 * Execute a workspace release with auto-alignment.
 *
 * <p>This is the {@code -publish} counterpart of {@code ws:release}
 * (which defaults to a draft preview). Before releasing, this goal
 * automatically aligns inter-component dependency versions so that
 * a release never ships with stale cross-references.
 *
 * <p>Usage: {@code mvn ws:release-apply}
 *
 * @see WsReleaseDraftMojo
 */
@Mojo(name = "release-publish", requiresProject = false, threadSafe = true)
public class WsReleasePublishMojo extends WsReleaseDraftMojo {

    @Override
    public void execute() throws MojoExecutionException {
        publish = true;
        autoAlign();
        super.execute();
    }

    private void autoAlign() throws MojoExecutionException {
        File root = workspaceRoot();
        String mvn = resolveMvnCommand(root);
        getLog().info("Auto-aligning workspace versions...");
        try {
            ReleaseSupport.exec(root, getLog(), mvn, "ws:align-apply", "-B");
        } catch (MojoExecutionException e) {
            getLog().warn("Auto-alignment completed with warnings: "
                    + e.getMessage());
        }
    }
}
