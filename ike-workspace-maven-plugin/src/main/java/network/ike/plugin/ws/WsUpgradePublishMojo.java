package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Execute workspace convention upgrades.
 *
 * <p>This is the {@code -publish} counterpart of {@code ws:upgrade}
 * (which defaults to a draft preview).
 *
 * <p>Usage: {@code mvn ws:upgrade-apply}
 *
 * @see WsUpgradeDraftMojo
 */
@Mojo(name = "upgrade-publish", requiresProject = false, threadSafe = true)
public class WsUpgradePublishMojo extends WsUpgradeDraftMojo {

    @Override
    public void execute() throws MojoExecutionException {
        publish = true;
        super.execute();
    }
}
