package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute a feature branch abandonment with confirmation.
 *
 * <p>This is the publish variant of {@link FeatureAbandonDraftMojo}.
 * It prompts for confirmation (unless {@code -Dforce=true}), then
 * deletes the feature branch across all components.
 *
 * <pre>{@code
 * mvn ws:feature-abandon-publish                     # with confirmation
 * mvn ws:feature-abandon-publish -Dforce=true        # skip confirmation
 * mvn ws:feature-abandon-publish -DdeleteRemote=true # also delete remote branches
 * }</pre>
 *
 * @see FeatureAbandonDraftMojo for the preview (draft) variant
 */
@Mojo(name = "feature-abandon-publish", requiresProject = false, threadSafe = true)
public class FeatureAbandonPublishMojo extends FeatureAbandonDraftMojo {

    /** Creates this goal instance. */
    public FeatureAbandonPublishMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        this.publish = true;
        super.execute();
    }
}
