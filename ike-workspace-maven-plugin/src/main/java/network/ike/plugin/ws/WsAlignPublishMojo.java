package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Apply inter-component version alignment.
 *
 * <p>This is the {@code -publish} counterpart of {@code ws:align}
 * (which defaults to a draft preview).
 *
 * <p>Usage: {@code mvn ws:align-apply}
 *
 * @see WsAlignDraftMojo
 */
@Mojo(name = "align-publish", requiresProject = false, threadSafe = true)
public class WsAlignPublishMojo extends WsAlignDraftMojo {

    @Override
    public void execute() throws MojoExecutionException {
        publish = true;
        super.execute();
    }
}
