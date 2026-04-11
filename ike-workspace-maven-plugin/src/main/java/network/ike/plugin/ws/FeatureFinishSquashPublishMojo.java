package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute a squash-merge of a feature branch.
 *
 * <p>This is the {@code -publish} counterpart of
 * {@code ws:feature-finish-squash} (which defaults to a draft preview).
 *
 * <p>Usage: {@code mvn ws:feature-finish-squash-apply -Dfeature=done -Dmessage="Ship it"}
 *
 * @see FeatureFinishSquashMojo
 */
@Mojo(name = "feature-finish-squash-publish", requiresProject = false, threadSafe = true)
public class FeatureFinishSquashPublishMojo extends FeatureFinishSquashDraftMojo {

    @Override
    public void execute() throws MojoExecutionException {
        publish = true;
        super.execute();
    }
}
