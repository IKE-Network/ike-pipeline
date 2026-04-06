package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute a feature branch abandon.
 *
 * <p>This is the {@code -apply} counterpart of {@code ws:feature-abandon}
 * (which defaults to a dry-run preview).
 *
 * <p>Usage: {@code mvn ws:feature-abandon-apply -Dfeature=dead-end}
 *
 * @see FeatureAbandonMojo
 */
@Mojo(name = "feature-abandon-apply", requiresProject = false, threadSafe = true)
public class FeatureAbandonApplyMojo extends FeatureAbandonMojo {

    @Override
    public void execute() throws MojoExecutionException {
        dryRun = false;
        super.execute();
    }
}
