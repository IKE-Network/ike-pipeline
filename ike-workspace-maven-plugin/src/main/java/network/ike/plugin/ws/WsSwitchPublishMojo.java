package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute a branch switch across workspace components.
 *
 * <p>This is the publish variant of {@link WsSwitchDraftMojo}.
 * It performs the actual checkout rather than previewing it.
 *
 * <pre>{@code
 * mvn ws:switch-publish                        # interactive
 * mvn ws:switch-publish -Dbranch=feature/foo   # non-interactive
 * }</pre>
 *
 * @see WsSwitchDraftMojo for the preview (draft) variant
 */
@Mojo(name = "switch-publish", requiresProject = false, threadSafe = true)
public class WsSwitchPublishMojo extends WsSwitchDraftMojo {

    /** Creates this goal instance. */
    public WsSwitchPublishMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        this.publish = true;
        super.execute();
    }
}
