package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute a squash-merge of a feature branch.
 *
 * <p>This is the {@code -apply} counterpart of
 * {@code ws:feature-finish-squash} (which defaults to a dry-run preview).
 *
 * <p>Usage: {@code mvn ws:feature-finish-squash-apply -Dfeature=done -Dmessage="Ship it"}
 *
 * @see FeatureFinishSquashMojo
 */
@Mojo(name = "feature-finish-squash-apply", requiresProject = false, threadSafe = true)
public class FeatureFinishSquashApplyMojo extends FeatureFinishSquashMojo {

    @Override
    public void execute() throws MojoExecutionException {
        dryRun = false;
        super.execute();
    }
}
