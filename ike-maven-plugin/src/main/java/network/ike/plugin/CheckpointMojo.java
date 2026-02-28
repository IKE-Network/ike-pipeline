package network.ike.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Create an immutable checkpoint.
 *
 * <p>Not yet implemented. Use the bash script fallback:
 * {@code target/build-tools/scripts/create-checkpoint.sh}
 */
@Mojo(name = "checkpoint", requiresProject = false)
public class CheckpointMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        getLog().warn("ike:checkpoint is not yet implemented.");
        getLog().info("Fallback: target/build-tools/scripts/create-checkpoint.sh");
    }
}
