package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Open the session report directory in the file manager or IDE.
 *
 * <p>Each ws: goal writes its latest output to a per-goal file
 * in the {@code session/} directory (e.g., {@code ws꞉overview.md}).
 * This goal opens that directory for browsing.
 *
 * <p>Usage: {@code mvn ws:report}
 */
@Mojo(name = "report", requiresProject = false, aggregator = true)
public class ReportMojo extends AbstractWorkspaceMojo {

    /** Creates this goal instance. */
    public ReportMojo() {}

    /**
     * Skip opening the file manager; just print the path.
     */
    @Parameter(property = "ws.report.printOnly", defaultValue = "false")
    private boolean printOnly;

    @Override
    public void execute() throws MojoExecutionException {
        Path sessionDir = WorkspaceReport.sessionDir(workspaceRoot().toPath());

        if (!Files.isDirectory(sessionDir)) {
            getLog().info("No session reports found. Run a ws: goal first.");
            return;
        }

        getLog().info("Session reports: " + sessionDir);

        if (!printOnly) {
            boolean opened = WorkspaceReport.openInBrowser(
                    workspaceRoot().toPath(), getLog());
            if (opened) {
                getLog().info("Opened session directory.");
            } else {
                getLog().info("Could not open file manager — browse directly: "
                        + sessionDir);
            }
        }
    }
}
