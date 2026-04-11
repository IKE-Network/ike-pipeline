package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute a rebase-and-fast-forward merge of a feature branch.
 *
 * <p>This is the {@code -publish} counterpart of
 * {@code ws:feature-finish-rebase} (which defaults to a draft preview).
 *
 * <p>Usage: {@code mvn ws:feature-finish-rebase-apply -Dfeature=small-fix}
 *
 * @see FeatureFinishRebaseDraftMojo
 */
@Mojo(name = "feature-finish-rebase-publish", requiresProject = false, threadSafe = true)
public class FeatureFinishRebasePublishMojo extends FeatureFinishRebaseDraftMojo {

    @Override
    public void execute() throws MojoExecutionException {
        publish = true;
        super.execute();
    }
}
