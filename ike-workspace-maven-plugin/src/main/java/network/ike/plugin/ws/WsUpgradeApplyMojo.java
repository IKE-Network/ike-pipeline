package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute workspace convention upgrades.
 *
 * <p>This is the {@code -apply} counterpart of {@code ws:upgrade}
 * (which defaults to a dry-run preview).
 *
 * <p>Usage: {@code mvn ws:upgrade-apply}
 *
 * @see WsUpgradeMojo
 */
@Mojo(name = "upgrade-apply", requiresProject = false, threadSafe = true)
public class WsUpgradeApplyMojo extends WsUpgradeMojo {

    @Override
    public void execute() throws MojoExecutionException {
        dryRun = false;
        super.execute();
    }
}
