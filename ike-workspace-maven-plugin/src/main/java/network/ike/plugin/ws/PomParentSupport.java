package network.ike.plugin.ws;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;  // retained for readParent()

/**
 * Utilities for reading and updating {@code <parent>} blocks in POM files.
 *
 * <p>Uses regex-based extraction for lightweight POM inspection without
 * requiring a full DOM parse. Thread-safe — all methods are stateless.
 */
final class PomParentSupport {

    private PomParentSupport() {}

    private static final Pattern PARENT_BLOCK = Pattern.compile(
            "(?s)<parent>\\s*" +
            "<groupId>([^<]+)</groupId>\\s*" +
            "<artifactId>([^<]+)</artifactId>\\s*" +
            "<version>([^<]*)</version>" +
            ".*?</parent>");

    /**
     * Read the parent block from a POM file.
     *
     * @param pomFile path to pom.xml
     * @return the parent info, or null if no parent block
     * @throws IOException if the file cannot be read
     */
    static ParentInfo readParent(Path pomFile) throws IOException {
        String content = Files.readString(pomFile, StandardCharsets.UTF_8);
        return readParent(content);
    }

    /**
     * Read the parent block from POM content.
     *
     * @param pomContent POM XML as a string
     * @return the parent info, or null if no parent block
     */
    static ParentInfo readParent(String pomContent) {
        Matcher m = PARENT_BLOCK.matcher(pomContent);
        if (m.find()) {
            return new ParentInfo(m.group(1), m.group(2), m.group(3));
        }
        return null;
    }

    /**
     * Update the parent version for a matching artifactId.
     * Delegates to {@link PomRewriter} for AST-aware manipulation.
     *
     * @param pomContent      POM XML as a string
     * @param parentArtifactId the artifactId to match in the parent block
     * @param newVersion      the new version to set
     * @return updated POM content (unchanged if no match)
     */
    static String updateParentVersion(String pomContent,
                                       String parentArtifactId,
                                       String newVersion) {
        return PomRewriter.updateParentVersion(
                pomContent, parentArtifactId, newVersion);
    }

    /**
     * Parsed parent block from a POM file.
     *
     * @param groupId    parent groupId
     * @param artifactId parent artifactId
     * @param version    parent version
     */
    record ParentInfo(String groupId, String artifactId, String version) {}
}
