package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Apply inter-component version alignment.
 *
 * <p>This is the {@code -apply} counterpart of {@code ws:align}
 * (which defaults to a dry-run preview).
 *
 * <p>Usage: {@code mvn ws:align-apply}
 *
 * @see WsAlignMojo
 */
@Mojo(name = "align-apply", requiresProject = false, threadSafe = true)
public class WsAlignApplyMojo extends WsAlignMojo {

    @Override
    public void execute() throws MojoExecutionException {
        dryRun = false;
        super.execute();
    }
}
