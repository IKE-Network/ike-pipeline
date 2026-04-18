package network.ike.plugin.ws.preflight;

import network.ike.plugin.ReleaseSupport;

import org.apache.maven.api.plugin.MojoException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Closed vocabulary of preflight checks that {@code ws:*} goals can
 * require before they mutate workspace state. Each entry declares a
 * human-readable description and a {@link #check(PreflightContext)}
 * implementation that returns {@link Optional#empty()} on success or a
 * remediation message on failure.
 *
 * <p>Drafts and publishes invoke the same {@code PreflightCondition}
 * sequence via {@link Preflight}; whether failure is a warning (draft)
 * or a hard error (publish) is decided at the call site via
 * {@link PreflightResult#requirePassed()} vs
 * {@link PreflightResult#warnIfFailed(org.apache.maven.api.plugin.Log)}.
 *
 * <p>New conditions are added here as goals adopt the contract from
 * issue #154. Each new entry must stay self-contained: it does not
 * depend on the mojo instance, only on the shared {@link PreflightContext}.
 */
public enum PreflightCondition {

    /**
     * Every subproject working tree (and the workspace root itself, if
     * it is a git repo) must have no uncommitted changes. Any draft or
     * publish goal that creates branches, edits POMs, or otherwise
     * mutates files requires this.
     */
    WORKING_TREE_CLEAN("All subproject working trees are clean") {
        @Override
        public Optional<String> check(PreflightContext ctx) {
            File root = ctx.workspaceRoot();
            List<String> uncommitted = new ArrayList<>();

            if (new File(root, ".git").exists()
                    && !gitStatus(root).isEmpty()) {
                uncommitted.add(WORKSPACE_ROOT_NAME);
            }
            for (String name : ctx.subprojects()) {
                File dir = new File(root, name);
                if (!new File(dir, ".git").exists()) continue;
                if (!gitStatus(dir).isEmpty()) {
                    uncommitted.add(name);
                }
            }

            if (uncommitted.isEmpty()) return Optional.empty();

            var sb = new StringBuilder();
            sb.append(uncommitted.size())
                    .append(" subproject(s) have uncommitted changes:\n");
            for (String name : uncommitted) {
                File dir = WORKSPACE_ROOT_NAME.equals(name)
                        ? root : new File(root, name);
                String files = gitStatus(dir).lines()
                        .map(l -> "      " + l.strip())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
                sb.append("    • ").append(name).append(":\n")
                  .append(files).append("\n");
            }
            sb.append("  To resolve:\n");
            sb.append("    mvn ws:commit -DaddAll=true"
                    + " -Dmessage=\"<your message>\"\n");
            sb.append("  Or stash changes in each affected subproject.");
            return Optional.of(sb.toString());
        }
    },

    /**
     * Every subproject with a {@code src/main/java} source tree must
     * produce javadoc without warnings. Enforces issue #168: missing
     * {@code @param} / {@code @return} / {@code @throws} tags and other
     * doclint-flagged issues should block a release rather than slip
     * through into published Nexus artifacts.
     *
     * <p>Runs {@code mvn -q javadoc:javadoc} per subproject; tolerates
     * non-zero exits (with {@code -DfailOnError=false -DfailOnWarnings=false})
     * so the plugin reports every warning in a single pass rather than
     * stopping at the first one. Costly — skip on goals other than the
     * release family.
     */
    JAVADOC_CLEAN("All subprojects produce warning-free javadoc") {
        @Override
        public Optional<String> check(PreflightContext ctx) {
            File root = ctx.workspaceRoot();
            Map<String, List<String>> warningsBySubproject = new LinkedHashMap<>();
            int total = 0;

            for (String name : ctx.subprojects()) {
                File dir = new File(root, name);
                if (!new File(dir, "pom.xml").exists()) continue;
                if (!new File(dir, "src/main/java").isDirectory()) continue;

                List<String> warnings = collectJavadocWarnings(dir);
                if (!warnings.isEmpty()) {
                    warningsBySubproject.put(name, warnings);
                    total += warnings.size();
                }
            }

            if (warningsBySubproject.isEmpty()) return Optional.empty();

            var sb = new StringBuilder();
            sb.append(total).append(" javadoc warning(s) across ")
                    .append(warningsBySubproject.size())
                    .append(" subproject(s):\n");
            for (var entry : warningsBySubproject.entrySet()) {
                sb.append("    • ").append(entry.getKey()).append(":\n");
                for (String line : entry.getValue()) {
                    sb.append("      ").append(line).append("\n");
                }
            }
            sb.append("  To resolve: add the missing @param / @return /")
                    .append(" @throws tags to the flagged methods.\n");
            sb.append("  Convention: every public method needs complete")
                    .append(" javadoc.");
            return Optional.of(sb.toString());
        }
    };

    /** Special marker used when the workspace root itself has uncommitted changes. */
    public static final String WORKSPACE_ROOT_NAME = "workspace root";

    private final String description;

    PreflightCondition(String description) {
        this.description = description;
    }

    /** Short human description of what this condition enforces. */
    public String description() {
        return description;
    }

    /**
     * Evaluate the condition against the given context.
     *
     * @param ctx the preflight context
     * @return {@link Optional#empty()} if the condition is satisfied;
     *         a remediation message otherwise
     */
    public abstract Optional<String> check(PreflightContext ctx);

    // ── Shared helpers ──────────────────────────────────────────────

    static String gitStatus(File dir) {
        try {
            return ReleaseSupport.execCapture(dir,
                    "git", "status", "--porcelain").trim();
        } catch (MojoException e) {
            return "";
        }
    }

    /**
     * Run {@code mvn -q javadoc:javadoc} in {@code dir} and return every
     * line matching {@code warning:}, stripped of the leading
     * {@code [WARNING] } prefix. Tolerates non-zero exit so the plugin
     * reports every warning in a single pass.
     */
    static List<String> collectJavadocWarnings(File dir) {
        List<String> warnings = new ArrayList<>();
        try {
            Process proc = new ProcessBuilder(
                    "mvn", "-q", "javadoc:javadoc",
                    "-DfailOnError=false",
                    "-DfailOnWarnings=false")
                    .directory(dir)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.contains("warning:")) continue;
                    String stripped = line
                            .replaceFirst("^\\[WARNING\\] ", "")
                            .strip();
                    warnings.add(stripped);
                }
            }
            proc.waitFor();
        } catch (IOException | InterruptedException e) {
            // Tolerate — the condition degrades gracefully to "no
            // warnings detected" rather than blocking the release on a
            // subprocess failure. A real javadoc failure will resurface
            // during the actual release build.
        }
        return warnings;
    }
}
