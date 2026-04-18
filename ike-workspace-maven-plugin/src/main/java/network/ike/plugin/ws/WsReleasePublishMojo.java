package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;

import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;

import java.io.File;

/**
 * Execute a workspace release with auto-alignment.
 *
 * <p>This is the {@code -publish} counterpart of {@code ws:release}
 * (which defaults to a draft preview). Before releasing, this goal
 * automatically aligns inter-subproject dependency versions so that
 * a release never ships with stale cross-references.
 *
 * <p>Usage: {@code mvn ws:release-publish}
 *
 * @see WsReleaseDraftMojo
 */
@Mojo(name = "release-publish", projectRequired = false)
public class WsReleasePublishMojo extends WsReleaseDraftMojo {

    /** Creates this goal instance. */
    public WsReleasePublishMojo() {}

    @Override
    public void execute() throws MojoException {
        publish = true;
        autoAlign();
        super.execute();
    }

    private void autoAlign() throws MojoException {
        File root = workspaceRoot();
        String mvn = resolveMvnCommand(root);
        getLog().info("Auto-aligning workspace versions...");
        try {
            ReleaseSupport.exec(root, getLog(), mvn,
                    WsGoal.ALIGN_PUBLISH.qualified(), "-B");
        } catch (MojoException e) {
            getLog().warn("Auto-alignment completed with warnings: "
                    + e.getMessage());
        }
    }
}
