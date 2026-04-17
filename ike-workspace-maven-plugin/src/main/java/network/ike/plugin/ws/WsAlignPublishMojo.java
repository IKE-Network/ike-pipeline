package network.ike.plugin.ws;

import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;

/**
 * Apply inter-component version alignment.
 *
 * <p>This is the {@code -publish} counterpart of {@code ws:align}
 * (which defaults to a draft preview).
 *
 * <p>Usage: {@code mvn ws:align-publish}
 *
 * @see WsAlignDraftMojo
 */
@Mojo(name = WsAlignPublishMojo.GOAL_NAME, projectRequired = false)
public class WsAlignPublishMojo extends WsAlignDraftMojo {

    /**
     * The Maven goal name for this mojo, without the {@code ws:} prefix.
     * Callers that invoke this goal as a subprocess should reference this
     * constant so a rename stays compiler-visible across call sites.
     */
    public static final String GOAL_NAME = "align-publish";

    /** Creates this goal instance. */
    public WsAlignPublishMojo() {}

    @Override
    public void execute() throws MojoException {
        publish = true;
        super.execute();
    }
}
