package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute a no-fast-forward merge of a feature branch.
 *
 * <p>This is the {@code -publish} counterpart of
 * {@code ws:feature-finish-merge} (which defaults to a draft preview).
 *
 * <p>Usage: {@code mvn ws:feature-finish-merge-apply -Dfeature=long-running}
 *
 * @see FeatureFinishMergeDraftMojo
 */
@Mojo(name = "feature-finish-merge-publish", requiresProject = false, threadSafe = true)
public class FeatureFinishMergePublishMojo extends FeatureFinishMergeDraftMojo {

    /** Creates this goal instance. */
    public FeatureFinishMergePublishMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        publish = true;
        super.execute();
    }
}
