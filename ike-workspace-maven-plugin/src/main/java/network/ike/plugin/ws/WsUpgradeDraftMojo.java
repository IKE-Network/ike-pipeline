package network.ike.plugin.ws;

import network.ike.workspace.IdeSettings;
import network.ike.workspace.Manifest;
import network.ike.workspace.ManifestException;
import network.ike.workspace.ManifestReader;

import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Upgrade workspace conventions to the current plugin version.
 *
 * <p>As workspace conventions evolve across plugin releases, this goal
 * applies incremental upgrades to bring an existing workspace in line
 * with current standards. Each upgrade step is idempotent — running
 * the goal twice produces the same result.
 *
 * <h2>Current upgrade steps</h2>
 * <ul>
 *   <li><b>gitignore-vcs-state</b> — ensure {@code .ike/vcs-state}
 *       is in the global gitignore (VCS bridge coordination file
 *       should be synced by Syncthing, not tracked by git)</li>
 *   <li><b>gitignore-whitelist</b> — ensure workspace .gitignore
 *       uses the whitelist strategy and includes all standard entries,
 *       grouped into sections (workspace-level files, workspace-owned
 *       directories, curated IntelliJ {@code .idea/} slice). When a
 *       section has no entries in the existing file, the full section
 *       is appended with its header comment; when partially present,
 *       only the missing entries are appended.</li>
 *   <li><b>stignore-delete-flag</b> — ensure {@code stignore-shared}
 *       uses {@code (?d)} prefix on directory ignore patterns</li>
 *   <li><b>pom-root-attribute</b> — ensure workspace POM has
 *       {@code root="true"} for Maven 4.1.0 reactor boundary</li>
 *   <li><b>maven-config</b> — ensure {@code .mvn/maven.config} exists</li>
 *   <li><b>ide-language-level</b> — when workspace.yaml has an
 *       {@code ide:} section and {@code .idea/misc.xml} exists, write
 *       the requested {@code language-level} and {@code jdk-name} to
 *       the {@code ProjectRootManager} component. Skipped cleanly when
 *       either input is absent. This is how projects using
 *       {@code --enable-preview} keep IntelliJ in sync with the POM.</li>
 *   <li><b>plugin-version</b> — update {@code ike-tooling.version}
 *       property in the workspace POM to the current plugin version</li>
 * </ul>
 *
 * <pre>{@code
 * mvn ws:upgrade              # apply all upgrades
 * mvn ws:upgrade -DdryRun     # preview what would change
 * }</pre>
 *
 * @see WsCreateMojo for creating a new workspace
 */
@Mojo(name = "upgrade-draft", projectRequired = false)
public class WsUpgradeDraftMojo extends AbstractWorkspaceMojo {

    /**
     * Show what would change without modifying any files.
     */
    @Parameter(property = "publish", defaultValue = "false")
    boolean publish;

    /** Creates this goal instance. */
    public WsUpgradeDraftMojo() {}

    @Override
    public void execute() throws MojoException {
        File root = workspaceRoot();
        Path rootPath = root.toPath();

        boolean draft = !publish;
        String pluginVersion = getClass().getPackage().getImplementationVersion();
        if (pluginVersion == null) pluginVersion = "49";

        getLog().info("");
        getLog().info(header("Upgrade"));
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("  Workspace: " + root.getName());
        getLog().info("  Plugin:    " + pluginVersion);
        if (draft) {
            getLog().info("  Mode:      DRAFT");
        }
        getLog().info("");

        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        // ── 1. Global gitignore: .ike/vcs-state ─────────────────
        upgradeGlobalGitignore(applied, skipped);

        // ── 2. Workspace .gitignore whitelist ───────────────────
        upgradeWorkspaceGitignore(rootPath, applied, skipped);

        // ── 3. stignore-shared (?d) flags ───────────────────────
        upgradeStignoreShared(rootPath, applied, skipped);

        // ── 4. POM root="true" ──────────────────────────────────
        upgradePomRoot(rootPath, applied, skipped);

        // ── 5. .mvn/maven.config ────────────────────────────────
        upgradeMavenConfig(rootPath, applied, skipped);

        // ── 6. IntelliJ language level (.idea/misc.xml) ─────────
        upgradeIdeLanguageLevel(rootPath, applied, skipped);

        // ── 7. Plugin version ───────────────────────────────────
        upgradePluginVersion(rootPath, pluginVersion, applied, skipped);

        // ── Summary ─────────────────────────────────────────────
        getLog().info("");
        getLog().info("  " + applied.size() + " upgrade(s) applied, "
                + skipped.size() + " already current.");
        if (draft && !applied.isEmpty()) {
            getLog().info("  (DRAFT — no files modified)");
        }
        getLog().info("");

        writeReport(publish ? WsGoal.UPGRADE_PUBLISH : WsGoal.UPGRADE_DRAFT,
                "**" + applied.size() + "** upgrade(s) applied, **"
                        + skipped.size() + "** already current."
                        + (draft && !applied.isEmpty() ? " (DRAFT)" : "") + "\n");
    }

    // ── Upgrade steps ────────────────────────────────────────────

    private void upgradeGlobalGitignore(List<String> applied, List<String> skipped) {
        Path globalIgnore = Path.of(System.getProperty("user.home"), ".gitignore_global");
        try {
            String content = Files.exists(globalIgnore)
                    ? Files.readString(globalIgnore, StandardCharsets.UTF_8) : "";

            boolean needsVcsState = !content.contains("vcs-state");
            boolean needsGitInit = !content.contains("_git-init");

            if (!needsVcsState && !needsGitInit) {
                skipped.add("global-gitignore");
                getLog().info(Ansi.green("  ✓ ") + "Global gitignore: already current");
                return;
            }

            StringBuilder additions = new StringBuilder();
            if (needsGitInit) additions.append("_git-init*\n");
            if (needsVcsState) additions.append(".ike/vcs-state\n");

            if (publish) {
                Files.writeString(globalIgnore,
                        content + (content.endsWith("\n") ? "" : "\n") + additions,
                        StandardCharsets.UTF_8);
            }

            applied.add("global-gitignore");
            getLog().info(Ansi.cyan("  ↑ ") + "Global gitignore: added "
                    + (needsVcsState ? ".ike/vcs-state " : "")
                    + (needsGitInit ? "_git-init* " : ""));
        } catch (IOException e) {
            getLog().warn(Ansi.yellow("  ⚠ ") + "Could not update global gitignore: " + e.getMessage());
        }
    }

    /**
     * Required {@code .gitignore} entries grouped into named sections.
     * Order mirrors what {@link WsCreateMojo#generateGitignore()} emits
     * so that output from {@code ws:create} and {@code ws:upgrade}
     * remains visually consistent.
     */
    static final List<GitignoreSection> GITIGNORE_SECTIONS = List.of(
            new GitignoreSection(
                    "# ── Whitelist workspace-level files ──────────────────────────────",
                    "!.gitignore", "!pom.xml", "!workspace.yaml"
            ),
            new GitignoreSection(
                    "# ── Whitelist workspace-owned directories ────────────────────────",
                    "!.mvn/", "!.mvn/**", "!checkpoints/", "!checkpoints/**"
            ),
            new GitignoreSection(
                    "# ── IntelliJ project config (curated slice) ──────────────────────\n"
                            + "# Small, stable project-wide settings shared across collaborators.\n"
                            + "# compiler.xml and vcs.xml are excluded — they regenerate per\n"
                            + "# Maven reload or per workspace membership.",
                    "!.idea/", "!.idea/.gitignore", "!.idea/misc.xml",
                    "!.idea/kotlinc.xml", "!.idea/encodings.xml",
                    "!.idea/jarRepositories.xml"
            )
    );

    /**
     * A named group of {@code .gitignore} entries sharing a section
     * header comment. Upgrade emits the full header when no entries
     * from this section are present; otherwise appends only the
     * missing entries individually.
     *
     * @param header  the section header comment block (no trailing newline)
     * @param entries the required entries for this section
     */
    record GitignoreSection(String header, List<String> entries) {
        GitignoreSection(String header, String... entries) {
            this(header, List.of(entries));
        }
    }

    /**
     * Compute the additions needed to bring an existing {@code .gitignore}
     * up to spec. Package-private for test access.
     *
     * @param content the current {@code .gitignore} content
     * @return the text to append (empty string if already current)
     */
    static String computeGitignoreAdditions(String content) {
        Set<String> existingLines = new LinkedHashSet<>();
        for (String line : content.split("\n")) {
            existingLines.add(line.trim());
        }

        StringBuilder additions = new StringBuilder();
        for (GitignoreSection section : GITIGNORE_SECTIONS) {
            List<String> missing = new ArrayList<>();
            for (String entry : section.entries()) {
                if (!existingLines.contains(entry)) {
                    missing.add(entry);
                }
            }
            if (missing.isEmpty()) {
                continue;
            }
            boolean fullSection = missing.size() == section.entries().size();
            if (fullSection) {
                additions.append("\n").append(section.header()).append("\n");
            }
            for (String entry : missing) {
                additions.append(entry).append("\n");
            }
        }
        return additions.toString();
    }

    private void upgradeWorkspaceGitignore(Path root, List<String> applied,
                                            List<String> skipped) {
        Path gitignore = root.resolve(".gitignore");
        try {
            if (!Files.exists(gitignore)) {
                skipped.add("workspace-gitignore");
                getLog().info("  - Workspace .gitignore: not present (skipped)");
                return;
            }

            String content = Files.readString(gitignore, StandardCharsets.UTF_8);
            String additions = computeGitignoreAdditions(content);

            if (additions.isEmpty()) {
                skipped.add("workspace-gitignore");
                getLog().info(Ansi.green("  ✓ ") + "Workspace .gitignore: already current");
                return;
            }

            if (publish) {
                Files.writeString(gitignore,
                        content + (content.endsWith("\n") ? "" : "\n") + additions,
                        StandardCharsets.UTF_8);
            }
            applied.add("workspace-gitignore");
            getLog().info(Ansi.cyan("  ↑ ") + "Workspace .gitignore: added missing whitelist entries");
        } catch (IOException e) {
            getLog().warn(Ansi.yellow("  ⚠ ") + "Could not update .gitignore: " + e.getMessage());
        }
    }

    /**
     * Enforce the IntelliJ language level (and optionally the JDK name)
     * declared in {@code workspace.yaml}'s {@code ide:} section. Targets
     * the {@code ProjectRootManager} component in {@code .idea/misc.xml}.
     *
     * <p>Skipped cleanly when {@code .idea/misc.xml} is absent (workspace
     * does not use IntelliJ) or when the manifest has no {@code ide:}
     * section. Idempotent — only writes when the target attribute value
     * differs from the manifest.
     *
     * @param root    workspace root directory
     * @param applied steps that modified files
     * @param skipped steps that made no change
     */
    private void upgradeIdeLanguageLevel(Path root, List<String> applied,
                                          List<String> skipped) {
        Path misc = root.resolve(".idea").resolve("misc.xml");
        if (!Files.exists(misc)) {
            skipped.add("ide-language-level");
            getLog().info("  - IDE language level: .idea/misc.xml not present (skipped)");
            return;
        }

        IdeSettings ide;
        try {
            Manifest m = ManifestReader.read(resolveManifest());
            ide = m.ide();
        } catch (ManifestException e) {
            getLog().warn(Ansi.yellow("  ⚠ ") + "IDE language level: could not read workspace.yaml: "
                    + e.getMessage());
            return;
        }

        if (!ide.hasAnyValue()) {
            skipped.add("ide-language-level");
            getLog().info("  - IDE language level: no 'ide' section in workspace.yaml (skipped)");
            return;
        }

        try {
            String content = Files.readString(misc, StandardCharsets.UTF_8);
            String updated = applyIdeSettings(content, ide);
            if (updated.equals(content)) {
                skipped.add("ide-language-level");
                getLog().info(Ansi.green("  ✓ ") + "IDE language level: already "
                        + (ide.languageLevel() != null ? ide.languageLevel() : "(unchanged)"));
                return;
            }

            if (publish) {
                Files.writeString(misc, updated, StandardCharsets.UTF_8);
            }
            applied.add("ide-language-level");
            getLog().info(Ansi.cyan("  ↑ ") + "IDE language level: set to "
                    + (ide.languageLevel() != null ? ide.languageLevel() : "(jdk-name only)"));
        } catch (IOException e) {
            getLog().warn(Ansi.yellow("  ⚠ ") + "Could not update .idea/misc.xml: " + e.getMessage());
        }
    }

    /**
     * Apply {@code ide.languageLevel} and {@code ide.jdkName} to the
     * {@code ProjectRootManager} component in {@code .idea/misc.xml}
     * content. Returns the updated content, or the original if no
     * change is needed. Package-private for test access.
     *
     * @param content the current {@code misc.xml} content
     * @param ide     the settings to enforce (null fields are no-ops)
     * @return the updated content
     */
    static String applyIdeSettings(String content, IdeSettings ide) {
        String updated = content;
        if (ide.languageLevel() != null) {
            updated = replaceProjectRootAttr(updated, "languageLevel", ide.languageLevel());
        }
        if (ide.jdkName() != null) {
            updated = replaceProjectRootAttr(updated, "project-jdk-name", ide.jdkName());
        }
        return updated;
    }

    private static String replaceProjectRootAttr(String content, String attr, String value) {
        Pattern p = Pattern.compile(
                "(<component\\s+name=\"ProjectRootManager\"[^>]*\\b"
                        + Pattern.quote(attr) + "=\")([^\"]*)(\")");
        Matcher m = p.matcher(content);
        if (!m.find()) {
            return content;
        }
        if (m.group(2).equals(value)) {
            return content;
        }
        return m.replaceFirst(Matcher.quoteReplacement(m.group(1))
                + Matcher.quoteReplacement(value)
                + Matcher.quoteReplacement(m.group(3)));
    }

    private void upgradeStignoreShared(Path root, List<String> applied,
                                        List<String> skipped) {
        // Look for stignore-shared in ike-dev root (parent of workspace)
        Path stignore = root.getParent() != null
                ? root.getParent().resolve("stignore-shared") : null;
        if (stignore == null || !Files.exists(stignore)) {
            skipped.add("stignore-shared");
            getLog().info("  - stignore-shared: not found (Syncthing not configured?)");
            return;
        }

        try {
            String content = Files.readString(stignore, StandardCharsets.UTF_8);
            boolean changed = false;
            String updated = content;

            // Upgrade patterns that lack (?d) prefix
            String[][] upgrades = {
                    {"\n.git/", "\n(?d).git/"},
                    {"\ntarget/", "\n(?d)target/"},
                    {"\nout/", "\n(?d)out/"},
                    {"\n.gradle/", "\n(?d).gradle/"},
                    {"\nbuild/", "\n(?d)build/"},
            };

            for (String[] pair : upgrades) {
                if (updated.contains(pair[0]) && !updated.contains(pair[1])) {
                    updated = updated.replace(pair[0], pair[1]);
                    changed = true;
                }
            }
            // Handle first line (no leading newline)
            if (updated.startsWith(".git/") && !updated.startsWith("(?d).git/")) {
                updated = "(?d)" + updated;
                changed = true;
            }

            if (!changed) {
                skipped.add("stignore-shared");
                getLog().info(Ansi.green("  ✓ ") + "stignore-shared: (?d) flags already present");
                return;
            }

            if (publish) {
                Files.writeString(stignore, updated, StandardCharsets.UTF_8);
            }
            applied.add("stignore-shared");
            getLog().info(Ansi.cyan("  ↑ ") + "stignore-shared: added (?d) prefix to directory patterns");
        } catch (IOException e) {
            getLog().warn(Ansi.yellow("  ⚠ ") + "Could not update stignore-shared: " + e.getMessage());
        }
    }

    private void upgradePomRoot(Path root, List<String> applied,
                                 List<String> skipped) {
        Path pom = root.resolve("pom.xml");
        try {
            if (!Files.exists(pom)) {
                skipped.add("pom-root");
                return;
            }

            String content = Files.readString(pom, StandardCharsets.UTF_8);
            if (content.contains("root=\"true\"")) {
                skipped.add("pom-root");
                getLog().info(Ansi.green("  ✓ ") + "POM root attribute: already present");
                return;
            }

            // Add root="true" to <project> element
            String updated = content.replaceFirst(
                    "(<project\\s[^>]*?)(>)",
                    "$1\n         root=\"true\"$2");

            if (publish) {
                Files.writeString(pom, updated, StandardCharsets.UTF_8);
            }
            applied.add("pom-root");
            getLog().info(Ansi.cyan("  ↑ ") + "POM: added root=\"true\"");
        } catch (IOException e) {
            getLog().warn(Ansi.yellow("  ⚠ ") + "Could not update pom.xml: " + e.getMessage());
        }
    }

    private void upgradeMavenConfig(Path root, List<String> applied,
                                     List<String> skipped) {
        Path config = root.resolve(".mvn/maven.config");
        try {
            if (Files.exists(config)) {
                skipped.add("maven-config");
                getLog().info(Ansi.green("  ✓ ") + ".mvn/maven.config: already present");
                return;
            }

            if (publish) {
                Files.createDirectories(config.getParent());
                Files.writeString(config, "-T 1C\n", StandardCharsets.UTF_8);
            }
            applied.add("maven-config");
            getLog().info(Ansi.cyan("  ↑ ") + ".mvn/maven.config: created with -T 1C");
        } catch (IOException e) {
            getLog().warn(Ansi.yellow("  ⚠ ") + "Could not create .mvn/maven.config: " + e.getMessage());
        }
    }

    private void upgradePluginVersion(Path root, String pluginVersion,
                                       List<String> applied, List<String> skipped) {
        Path pom = root.resolve("pom.xml");
        try {
            if (!Files.exists(pom)) {
                skipped.add("plugin-version");
                return;
            }

            String content = Files.readString(pom, StandardCharsets.UTF_8);
            boolean changed = false;

            // Migration: rename ike-maven-plugin.version → ike-tooling.version
            // Also update the ${...} reference in the plugin version element
            if (content.contains("<ike-maven-plugin.version>")) {
                content = content.replace(
                        "<ike-maven-plugin.version>", "<ike-tooling.version>");
                content = content.replace(
                        "</ike-maven-plugin.version>", "</ike-tooling.version>");
                content = content.replace(
                        "${ike-maven-plugin.version}", "${ike-tooling.version}");
                changed = true;
                getLog().info(Ansi.cyan("  ↑ ") + "Property renamed: ike-maven-plugin.version → ike-tooling.version");
            }

            // Find current ike-tooling.version property and bump
            java.util.regex.Pattern versionProp = java.util.regex.Pattern.compile(
                    "(<ike-tooling\\.version>)(.*?)(</ike-tooling\\.version>)");
            java.util.regex.Matcher m = versionProp.matcher(content);

            if (!m.find()) {
                if (!changed) {
                    skipped.add("plugin-version");
                    getLog().info("  - Plugin version: no ike-tooling.version property found");
                }
                return;
            }

            String currentVersion = m.group(2);
            if (!currentVersion.equals(pluginVersion)) {
                content = m.replaceFirst("$1" + pluginVersion + "$3");
                changed = true;
                getLog().info(Ansi.cyan("  ↑ ") + "Plugin version: " + currentVersion + " → " + pluginVersion);
            } else if (!changed) {
                skipped.add("plugin-version");
                getLog().info(Ansi.green("  ✓ ") + "Plugin version: already " + pluginVersion);
                return;
            }

            if (publish && changed) {
                Files.writeString(pom, content, StandardCharsets.UTF_8);
            }
            applied.add("plugin-version");
        } catch (IOException e) {
            getLog().warn(Ansi.yellow("  ⚠ ") + "Could not update plugin version: " + e.getMessage());
        }
    }
}
