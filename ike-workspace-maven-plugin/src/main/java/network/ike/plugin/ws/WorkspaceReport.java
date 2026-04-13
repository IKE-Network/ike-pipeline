package network.ike.plugin.ws;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Per-goal report writer for workspace goals.
 *
 * <p>Each goal writes its own file in the {@code session/} directory
 * at the workspace root. Files are <b>overwritten</b> on each run
 * (not appended), so the content always reflects the latest execution.
 *
 * <p>Filenames use {@code ꞉} (U+A789 MODIFIER LETTER COLON) to
 * cluster visually as {@code ws꞉goal-name.md} in IDE file browsers.
 * For draft/publish goals, the filename includes the variant:
 * {@code ws꞉feature-start-draft.md}, {@code ws꞉feature-start-publish.md}.
 *
 * <p>The {@code session/} directory is gitignored and survives {@code mvn clean}.
 */
public final class WorkspaceReport {

    /** U+A789 MODIFIER LETTER COLON — filesystem-safe visual colon. */
    private static final char COLON = '\uA789';

    private static final String SESSION_DIR = "session";
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private WorkspaceReport() {}

    /**
     * Write a goal's report to its per-goal file, overwriting any previous content.
     *
     * <p>Creates the {@code session/} directory if it doesn't exist.
     *
     * @param workspaceRoot the workspace root directory
     * @param goalName      the goal name including variant (e.g., "ws:feature-start-draft")
     * @param content       the markdown content to write
     * @param log           Maven logger (warnings only; null-safe)
     */
    public static void write(Path workspaceRoot, String goalName,
                              String content, Log log) {
        Path sessionDir = workspaceRoot.resolve(SESSION_DIR);
        String filename = "ws" + COLON + stripPrefix(goalName) + ".md";
        Path reportFile = sessionDir.resolve(filename);

        try {
            Files.createDirectories(sessionDir);

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
            String fullContent = "# " + goalName + "\n"
                    + "_" + timestamp + "_\n\n"
                    + content.stripTrailing() + "\n";

            Files.writeString(reportFile, fullContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (log != null) {
                log.debug("Could not write report " + filename + ": " + e.getMessage());
            }
        }
    }

    /**
     * Resolve the session directory path for the given workspace root.
     *
     * @param workspaceRoot the workspace root directory
     * @return path to the session directory (may not exist yet)
     */
    public static Path sessionDir(Path workspaceRoot) {
        return workspaceRoot.resolve(SESSION_DIR);
    }

    /**
     * Resolve the report file path for a specific goal.
     *
     * @param workspaceRoot the workspace root directory
     * @param goalName      the goal name (e.g., "ws:overview")
     * @return path to the report file (may not exist yet)
     */
    public static Path reportPath(Path workspaceRoot, String goalName) {
        String filename = "ws" + COLON + stripPrefix(goalName) + ".md";
        return workspaceRoot.resolve(SESSION_DIR).resolve(filename);
    }

    /**
     * Open the session directory in the default file manager or IDE.
     *
     * @param workspaceRoot the workspace root directory
     * @param log           Maven logger
     * @return true if opened successfully
     */
    public static boolean openInBrowser(Path workspaceRoot, Log log) {
        Path sessionPath = sessionDir(workspaceRoot);
        if (!Files.isDirectory(sessionPath)) {
            if (log != null) {
                log.warn("No session directory found at " + sessionPath);
            }
            return false;
        }

        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
                new ProcessBuilder("open", sessionPath.toString()).start();
                return true;
            }
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(sessionPath.toFile());
                return true;
            }
        } catch (IOException e) {
            if (log != null) {
                log.warn("Could not open session directory: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Strip the "ws:" prefix from a goal name for use in filenames.
     * "ws:overview" → "overview", "ws:feature-start-draft" → "feature-start-draft"
     */
    private static String stripPrefix(String goalName) {
        if (goalName.startsWith("ws:")) {
            return goalName.substring(3);
        }
        return goalName;
    }
}
