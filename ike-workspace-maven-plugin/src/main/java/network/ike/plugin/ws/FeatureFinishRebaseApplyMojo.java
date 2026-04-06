package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute a rebase-and-fast-forward merge of a feature branch.
 *
 * <p>This is the {@code -apply} counterpart of
 * {@code ws:feature-finish-rebase} (which defaults to a dry-run preview).
 *
 * <p>Usage: {@code mvn ws:feature-finish-rebase-apply -Dfeature=small-fix}
 *
 * @see FeatureFinishRebaseMojo
 */
@Mojo(name = "feature-finish-rebase-apply", requiresProject = false, threadSafe = true)
public class FeatureFinishRebaseApplyMojo extends FeatureFinishRebaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        dryRun = false;
        super.execute();
    }
}
