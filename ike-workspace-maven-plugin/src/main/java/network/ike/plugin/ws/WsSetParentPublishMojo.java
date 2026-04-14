package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Apply the aggregator parent version cascade.
 *
 * <p>This is the {@code -publish} counterpart of
 * {@code ws:set-parent-draft}. It updates the root POM and all
 * component POMs to the specified parent version.
 *
 * <pre>{@code
 * mvn ws:set-parent-publish -Dparent.version=92
 * }</pre>
 *
 * @see WsSetParentDraftMojo
 */
@Mojo(name = "set-parent-publish", requiresProject = false, threadSafe = true)
public class WsSetParentPublishMojo extends WsSetParentDraftMojo {

    /** Creates this goal instance. */
    public WsSetParentPublishMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        publish = true;
        super.execute();
    }
}
