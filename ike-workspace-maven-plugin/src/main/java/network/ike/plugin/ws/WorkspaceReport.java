package network.ike.plugin.ws;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe cumulative report writer for workspace goals.
 *
 * <p>Each goal appends a timestamped markdown section to
 * {@code target/ws-report.md} in the workspace root. The file
 * accumulates across a session and is removed by {@code mvn clean}.
 *
 * <p>File locking via {@link FileChannel#lock()} ensures safe
 * concurrent writes, honoring the {@code @ThreadSafe} contract
 * of Maven plugins.
 */
public final class WorkspaceReport {

    private static final String REPORT_FILENAME = "ws-report.md";
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private WorkspaceReport() {}

    /**
     * Append a goal's output to the workspace report file.
     *
     * <p>Creates the {@code target/} directory and report file if they
     * don't exist. Uses file-level locking for thread safety.
     *
     * @param workspaceRoot the workspace root directory
     * @param goalName      the goal that produced this output (e.g., "ws:add")
     * @param content       the markdown content to append
     * @param log           Maven logger (warnings only; null-safe)
     */
    public static void append(Path workspaceRoot, String goalName,
                               String content, Log log) {
        Path targetDir = workspaceRoot.resolve("target");
        Path reportFile = targetDir.resolve(REPORT_FILENAME);

        try {
            Files.createDirectories(targetDir);

            // File-level lock for thread safety
            try (RandomAccessFile raf = new RandomAccessFile(
                         reportFile.toFile(), "rw");
                 FileChannel channel = raf.getChannel();
                 FileLock ignored = channel.lock()) {

                // Seek to end for append
                long length = raf.length();
                raf.seek(length);

                // Write header on first section
                if (length == 0) {
                    String header = "# Workspace Report\n\n";
                    raf.write(header.getBytes(StandardCharsets.UTF_8));
                }

                // Timestamp + goal header + content
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
                String section = "## " + goalName + "\n"
                        + "_" + timestamp + "_\n\n"
                        + content.stripTrailing() + "\n\n---\n\n";
                raf.write(section.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            if (log != null) {
                log.debug("Could not write workspace report: " + e.getMessage());
            }
        }
    }

    /**
     * Resolve the report file path for the given workspace root.
     *
     * @param workspaceRoot the workspace root directory
     * @return path to the report file (may not exist yet)
     */
    public static Path reportPath(Path workspaceRoot) {
        return workspaceRoot.resolve("target").resolve(REPORT_FILENAME);
    }

    /**
     * Open the report file in the default browser.
     *
     * @param workspaceRoot the workspace root directory
     * @param log           Maven logger
     * @return true if the file was opened, false if it doesn't exist or open failed
     */
    public static boolean openInBrowser(Path workspaceRoot, Log log) {
        Path reportFile = reportPath(workspaceRoot);
        if (!Files.exists(reportFile)) {
            if (log != null) {
                log.warn("No report file found at " + reportFile);
            }
            return false;
        }

        try {
            // macOS: open in IDE (renders markdown natively)
            if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
                // Try IntelliJ IDEA first, then fall back to default app
                try {
                    new ProcessBuilder("open", "-a", "IntelliJ IDEA",
                            reportFile.toString()).start();
                    return true;
                } catch (IOException _) {
                    // IntelliJ not found — try default app
                    new ProcessBuilder("open", reportFile.toString()).start();
                    return true;
                }
            }
            // Fallback: java.awt.Desktop
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(reportFile.toFile());
                return true;
            }
        } catch (IOException e) {
            if (log != null) {
                log.warn("Could not open report in browser: " + e.getMessage());
            }
        }
        return false;
    }
}
