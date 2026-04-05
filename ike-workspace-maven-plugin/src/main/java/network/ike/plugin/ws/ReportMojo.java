package network.ike.plugin.ws;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Open the cumulative workspace report in the default browser.
 *
 * <p>The report file ({@code target/ws-report.md}) accumulates output
 * from all ws: goals run in this session. This goal opens it for review.
 *
 * <p>Usage: {@code mvn ws:report}
 */
@Mojo(name = "report", requiresProject = false, aggregator = true)
public class ReportMojo extends AbstractWorkspaceMojo {

    /**
     * Skip opening the browser; just print the report path.
     */
    @Parameter(property = "ws.report.printOnly", defaultValue = "false")
    private boolean printOnly;

    @Override
    public void execute() throws MojoExecutionException {
        Path reportFile = WorkspaceReport.reportPath(workspaceRoot().toPath());

        if (!Files.exists(reportFile)) {
            getLog().info("No report found. Run a ws: goal first.");
            return;
        }

        getLog().info("Report: " + reportFile);

        if (!printOnly) {
            boolean opened = WorkspaceReport.openInBrowser(
                    workspaceRoot().toPath(), getLog());
            if (opened) {
                getLog().info("Opened in browser.");
            } else {
                getLog().info("Could not open browser — view the file directly.");
            }
        }
    }
}
