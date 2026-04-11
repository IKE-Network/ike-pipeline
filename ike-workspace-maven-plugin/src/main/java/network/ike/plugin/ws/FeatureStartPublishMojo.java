package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;

/**
 * Start a feature branch with auto-alignment.
 *
 * <p>This is the {@code -publish} counterpart of {@code ws:feature-start}
 * (which defaults to a draft preview). Before creating feature branches,
 * this goal automatically aligns inter-component dependency versions so
 * that the feature branch starts from a consistent state.
 *
 * <p>Usage: {@code mvn ws:feature-start-apply -Dfeature=my-feature}
 *
 * @see FeatureStartDraftMojo
 */
@Mojo(name = "feature-start-publish", requiresProject = false, threadSafe = true)
public class FeatureStartPublishMojo extends FeatureStartDraftMojo {

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
