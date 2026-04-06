package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute a no-fast-forward merge of a feature branch.
 *
 * <p>This is the {@code -apply} counterpart of
 * {@code ws:feature-finish-merge} (which defaults to a dry-run preview).
 *
 * <p>Usage: {@code mvn ws:feature-finish-merge-apply -Dfeature=long-running}
 *
 * @see FeatureFinishMergeMojo
 */
@Mojo(name = "feature-finish-merge-apply", requiresProject = false, threadSafe = true)
public class FeatureFinishMergeApplyMojo extends FeatureFinishMergeMojo {

    @Override
    public void execute() throws MojoExecutionException {
        dryRun = false;
        super.execute();
    }
}
