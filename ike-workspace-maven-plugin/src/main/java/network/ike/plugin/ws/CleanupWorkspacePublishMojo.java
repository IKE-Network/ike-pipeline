package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute workspace cleanup — delete merged feature branches.
 *
 * <p>This is the {@code -publish} counterpart of {@code ws:cleanup}
 * (which defaults to a draft listing).
 *
 * <p>Usage: {@code mvn ws:cleanup-publish}
 *
 * @see CleanupWorkspaceMojo
 */
@Mojo(name = "cleanup-publish", requiresProject = false, threadSafe = true)
public class CleanupWorkspacePublishMojo extends CleanupWorkspaceMojo {

    @Override
    public void execute() throws MojoExecutionException {
        publish = true;
        super.execute();
    }
}
