package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute a feature branch update from main.
 *
 * <p>This is the publish variant of {@link UpdateFeatureDraftMojo}.
 * It performs the actual rebase or merge rather than previewing it.
 *
 * <pre>{@code
 * mvn ws:update-feature-publish                    # rebase (default)
 * mvn ws:update-feature-publish -Dstrategy=merge   # merge main into feature
 * }</pre>
 *
 * @see UpdateFeatureDraftMojo for the preview (draft) variant
 */
@Mojo(name = "update-feature-publish", requiresProject = false, threadSafe = true)
public class UpdateFeaturePublishMojo extends UpdateFeatureDraftMojo {

    /** Creates this goal instance. */
    public UpdateFeaturePublishMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        this.publish = true;
        super.execute();
    }
}
