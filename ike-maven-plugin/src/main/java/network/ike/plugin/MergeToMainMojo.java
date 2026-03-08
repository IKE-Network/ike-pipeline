package network.ike.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Merge a feature branch to main.
 *
 * <p>Not yet implemented. Use the bash script fallback:
 * {@code target/build-tools/scripts/merge-to-main.sh}
 */
@Mojo(name = "merge-to-main", requiresProject = false, threadSafe = true)
public class MergeToMainMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        getLog().warn("ike:merge-to-main is not yet implemented.");
        getLog().info("Fallback: target/build-tools/scripts/merge-to-main.sh");
    }
}
