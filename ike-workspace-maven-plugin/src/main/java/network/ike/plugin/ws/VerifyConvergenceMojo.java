package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;
import network.ike.workspace.DependencyConvergenceAnalysis;
import network.ike.workspace.DependencyConvergenceAnalysis.Divergence;
import network.ike.workspace.DependencyTreeParser;
import network.ike.workspace.DependencyTreeParser.ResolvedDependency;
import network.ike.workspace.WorkspaceGraph;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Check transitive dependency convergence across workspace components.
 *
 * <p>This goal runs {@code mvn dependency:tree} for each component in
 * topological order, then compares resolved versions of shared
 * dependencies. Divergences (the same artifact resolving to different
 * versions in different components) are reported in the terminal and
 * written to a markdown report.
 *
 * <p>This is inherently read-only — no apply variant is needed.
 * Slower than other verification goals because it invokes Maven
 * per component.
 *
 * <pre>{@code
 * mvn ws:verify-convergence
 * mvn ws:verify-convergence -DconvergenceReport=build/convergence.md
 * }</pre>
 *
 * @see VerifyWorkspaceMojo for general workspace verification
 */
@Mojo(name = "verify-convergence", requiresProject = false, threadSafe = true)
public class VerifyConvergenceMojo extends AbstractWorkspaceMojo {

    /**
     * Output file for the convergence markdown report. Defaults to
     * {@code target/convergence-report.md} in the workspace root.
     */
    @Parameter(property = "convergenceReport")
    String convergenceReport;

    /** Creates this goal instance. */
    public VerifyConvergenceMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        ReportLog report = startReport();

        getLog().info("");
        getLog().info(header("Dependency Convergence"));
        getLog().info("══════════════════════════════════════════════════════════════");

        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();

        getLog().info("");
        getLog().info("  Resolving dependency trees (this may take a while)...");
        getLog().info("");

        File mvnExecutable = resolveMvn(root);

        // Collect dependency trees per component in topological order
        List<String> order = graph.topologicalSort();
        Map<String, List<ResolvedDependency>> componentTrees =
                new LinkedHashMap<>();

        for (String name : order) {
            File compDir = new File(root, name);
            File pomFile = new File(compDir, "pom.xml");
            if (!pomFile.exists()) continue;

            getLog().info("    Resolving " + name + "...");
            try {
                String treeOutput = ReleaseSupport.execCapture(compDir,
                        mvnExecutable.getAbsolutePath(),
                        "dependency:tree", "-DoutputType=text",
                        "-B", "-q");
                List<ResolvedDependency> deps =
                        DependencyTreeParser.parse(treeOutput);
                if (!deps.isEmpty()) {
                    componentTrees.put(name, deps);
                }
            } catch (MojoExecutionException e) {
                getLog().warn("    ⚠ " + name + ": dependency:tree failed — "
                        + e.getMessage());
            }
        }

        if (componentTrees.size() < 2) {
            getLog().info("    Fewer than 2 components resolved — skipping analysis");
            finishReport("ws:verify-convergence", report);
            return;
        }

        // Analyze
        List<Divergence> divergences =
                DependencyConvergenceAnalysis.analyze(componentTrees);

        // Terminal output
        if (divergences.isEmpty()) {
            getLog().info("");
            getLog().info("  Convergence: all shared dependencies converge across "
                    + componentTrees.size() + " components  ✓");
        } else {
            getLog().info("");
            getLog().info("  Convergence: " + divergences.size()
                    + " artifact(s) diverge across "
                    + componentTrees.size() + " components");
            getLog().info("");

            for (Divergence d : divergences) {
                getLog().info("    " + d.coordinate());
                for (var vEntry : d.versionToComponents().entrySet()) {
                    getLog().info("      " + vEntry.getKey() + " ← "
                            + String.join(", ", vEntry.getValue()));
                }
            }
        }

        // Markdown report
        String wsName = workspaceName();
        String markdown = divergences.isEmpty()
                ? "# Dependency Convergence — " + wsName + "\n\n"
                + "All shared dependencies converge across "
                + componentTrees.size() + " components.\n"
                : DependencyConvergenceAnalysis.formatMarkdownReport(
                divergences, wsName);

        Path reportPath = resolveConvergenceReportPath(root);
        try {
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, markdown, StandardCharsets.UTF_8);
            getLog().info("");
            getLog().info("  Report: " + reportPath);
        } catch (IOException e) {
            getLog().warn("  Could not write convergence report: "
                    + e.getMessage());
        }

        getLog().info("");
        finishReport("ws:verify-convergence", report);
    }

    private File resolveMvn(File root) throws MojoExecutionException {
        File mvnw = new File(root, "mvnw");
        if (mvnw.exists() && mvnw.canExecute()) return mvnw;
        try {
            ReleaseSupport.execCapture(root, "mvn", "--version");
            return new File("mvn");
        } catch (MojoExecutionException e) {
            throw new MojoExecutionException(
                    "Cannot find mvnw or mvn. Place mvnw in the workspace "
                            + "root or ensure mvn is on PATH.");
        }
    }

    private Path resolveConvergenceReportPath(File root) {
        if (convergenceReport != null && !convergenceReport.isBlank()) {
            return Path.of(convergenceReport);
        }
        return root.toPath().resolve("target").resolve("convergence-report.md");
    }
}
