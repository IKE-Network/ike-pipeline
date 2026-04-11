package network.ike.plugin.ws;

import network.ike.plugin.ReleaseSupport;

import network.ike.workspace.Component;
import network.ike.workspace.Defaults;
import network.ike.workspace.WorkspaceGraph;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Clone and initialize workspace components from the manifest.
 *
 * <p>Three initialization modes per component:
 * <ol>
 *   <li><b>Already cloned</b> — directory has {@code .git/}; skip.</li>
 *   <li><b>Syncthing working tree</b> — directory exists but no
 *       {@code .git/}. Initializes git in-place: {@code git init},
 *       adds the remote, fetches, and resets to match the remote branch.
 *       This preserves file content synced from another machine.</li>
 *   <li><b>Fresh clone</b> — no directory; runs {@code git clone}.</li>
 * </ol>
 *
 * <p>Components are initialized in topological (dependency) order.
 *
 * <pre>{@code
 * mvn ike:init
 * mvn ike:init -Dgroup=studio
 * mvn ike:init -Dgroup=docs
 * }</pre>
 */
@Mojo(name = "init", requiresProject = false, threadSafe = true)
public class InitWorkspaceMojo extends AbstractWorkspaceMojo {

    /**
     * Restrict to a named group (or single component). Default: all.
     */
    @Parameter(property = "group")
    String group;

    /** Creates this goal instance. */
    public InitWorkspaceMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        WorkspaceGraph graph = loadGraph();
        File root = workspaceRoot();
        Defaults defaults = graph.manifest().defaults();

        Set<String> targets;
        if (group != null && !group.isEmpty()) {
            targets = graph.expandGroup(group);
        } else {
            targets = graph.manifest().components().keySet();
        }

        // Sort in dependency order
        List<String> sorted = graph.topologicalSort(new LinkedHashSet<>(targets));

        getLog().info("");
        getLog().info(header("Init"));
        getLog().info("══════════════════════════════════════════════════════════════");
        getLog().info("  Target: " + (group != null ? group : "all")
                + " (" + sorted.size() + " components)");
        getLog().info("  Root:   " + root.getAbsolutePath());
        if (defaults.mavenVersion() != null) {
            getLog().info("  Maven:  " + defaults.mavenVersion() + " (default)");
        }
        getLog().info("");

        int cloned = 0;
        int syncthing = 0;
        int skipped = 0;
        int wrappers = 0;
        List<String[]> rows = new ArrayList<>();

        for (String name : sorted) {
            Component component = graph.manifest().components().get(name);
            File dir = new File(root, name);
            File gitDir = new File(dir, ".git");

            if (gitDir.exists()) {
                // Already a git repo — still ensure wrapper, jvm.config, and CLAUDE.md are current
                getLog().info(Ansi.green("  ✓ ") + name + " — already initialized");
                if (ensureMavenWrapper(dir, component, defaults)) {
                    wrappers++;
                }
                ensureJvmConfig(dir);
                ensureClaudeNotes(dir.toPath(), name);
                writeComponentClaudeMd(dir.toPath(), component);
                checkoutSha(dir, component);
                skipped++;
                rows.add(new String[]{name, "present",
                        component.repo() != null ? component.repo() : "—", "✓"});
                continue;
            }

            String repo = component.repo();
            String branch = component.branch();

            if (repo == null || repo.isEmpty()) {
                getLog().warn(Ansi.yellow("  ⚠ ") + name + " — no repo URL, skipping");
                rows.add(new String[]{name, "skipped", "—", "no repo URL"});
                continue;
            }

            if (dir.exists()) {
                // Syncthing working tree — init git in-place
                getLog().info(Ansi.cyan("  ↻ ") + name
                        + " — initializing git in existing directory (Syncthing)");
                initSyncthingRepo(dir, repo, branch);
                installHooks(dir);
                if (ensureMavenWrapper(dir, component, defaults)) {
                    wrappers++;
                }
                ensureJvmConfig(dir);
                ensureClaudeNotes(dir.toPath(), name);
                writeComponentClaudeMd(dir.toPath(), component);
                checkoutSha(dir, component);
                syncthing++;
                rows.add(new String[]{name, "syncthing-init", repo, "✓"});
            } else {
                // Fresh clone
                getLog().info(Ansi.cyan("  ↓ ") + name + " — cloning from " + repo);
                cloneRepo(root, name, repo, branch);
                File componentDir = new File(root, name);
                installHooks(componentDir);
                if (ensureMavenWrapper(componentDir, component, defaults)) {
                    wrappers++;
                }
                ensureJvmConfig(componentDir);
                ensureClaudeNotes(componentDir.toPath(), name);
                writeComponentClaudeMd(componentDir.toPath(), component);
                checkoutSha(componentDir, component);
                cloned++;
                rows.add(new String[]{name, "cloned", repo, "✓"});
            }
        }

        // Ensure Maven wrapper at the workspace root itself (the aggregator POM)
        if (ensureWorkspaceRootWrapper(root, defaults)) {
            wrappers++;
        }

        getLog().info("");
        getLog().info("  Done: " + cloned + " cloned, " + syncthing
                + " Syncthing-initialized, " + skipped + " already present"
                + (wrappers > 0 ? ", " + wrappers + " Maven wrappers installed/updated" : ""));
        getLog().info("");

        // Generate goal cheatsheet, reference, and CLAUDE.md at workspace root
        writeGoalCheatsheet(root.toPath());
        writeWorkspaceReference(root.toPath());
        ensureClaudeNotes(root.toPath(), workspaceName());
        writeWorkspaceClaudeMd(root.toPath(), graph);

        // Structured markdown report (replaces console-log capture)
        appendReport("ws:init", buildInitMarkdownReport(
                rows, cloned, syncthing, skipped, wrappers));
    }

    private String buildInitMarkdownReport(List<String[]> rows,
                                            int cloned, int syncthing,
                                            int skipped, int wrappers) {
        var sb = new StringBuilder();
        sb.append(cloned).append(" cloned, ").append(syncthing)
                .append(" Syncthing-initialized, ").append(skipped)
                .append(" already present");
        if (wrappers > 0) {
            sb.append(", ").append(wrappers).append(" Maven wrappers updated");
        }
        sb.append(".\n\n");
        sb.append("| Component | Action | URL | Status |\n");
        sb.append("|-----------|--------|-----|--------|\n");
        for (String[] row : rows) {
            sb.append("| ").append(row[0])
                    .append(" | ").append(row[1])
                    .append(" | ").append(row[2])
                    .append(" | ").append(row[3])
                    .append(" |\n");
        }
        return sb.toString();
    }

    /**
     * Initialize a git repo inside an existing directory (Syncthing case).
     * The directory has files but no .git — we init, add remote, fetch,
     * and reset to match the remote branch without overwriting working-tree files.
     */
    private void initSyncthingRepo(File dir, String repo, String branch)
            throws MojoExecutionException {
        ReleaseSupport.exec(dir, getLog(), "git", "init");
        ReleaseSupport.exec(dir, getLog(), "git", "remote", "add", "origin", repo);
        ReleaseSupport.exec(dir, getLog(), "git", "fetch", "origin", branch);
        // Mixed reset: updates HEAD and index to match remote, keeps working tree
        ReleaseSupport.exec(dir, getLog(),
                "git", "reset", "origin/" + branch);
    }

    /**
     * Standard git clone into a new directory.
     */
    private void cloneRepo(File root, String name, String repo, String branch)
            throws MojoExecutionException {
        ReleaseSupport.exec(root, getLog(),
                "git", "clone", "-b", branch, repo, name);
    }

    /**
     * Resolve the effective Maven version for a component: component override,
     * then workspace default, then null (no wrapper).
     */
    private String resolveMavenVersion(Component component, Defaults defaults) {
        if (component.mavenVersion() != null) {
            return component.mavenVersion();
        }
        return defaults.mavenVersion();
    }

    /**
     * Ensure the Maven wrapper is installed at the workspace root directory.
     * The workspace aggregator POM needs the wrapper so that IntelliJ (and
     * command-line builds) resolve to the correct Maven version rather than
     * falling back to the system default.
     *
     * <p>Uses the workspace default maven-version from {@code workspace.yaml}.
     *
     * @param root     the workspace root directory
     * @param defaults workspace defaults
     * @return true if wrapper was installed or updated
     */
    private boolean ensureWorkspaceRootWrapper(File root, Defaults defaults) {
        String mavenVersion = defaults.mavenVersion();
        if (mavenVersion == null) {
            return false;
        }

        File pomFile = new File(root, "pom.xml");
        if (!pomFile.exists()) {
            return false;
        }

        try {
            Path wrapperDir = root.toPath().resolve(".mvn").resolve("wrapper");
            Path propsFile = wrapperDir.resolve("maven-wrapper.properties");

            if (propsFile.toFile().exists()) {
                Properties existing = new Properties();
                try (var reader = Files.newBufferedReader(propsFile, StandardCharsets.UTF_8)) {
                    existing.load(reader);
                }
                String currentVersion = existing.getProperty("maven.version");
                if (mavenVersion.equals(currentVersion)) {
                    getLog().debug("    Workspace root Maven wrapper already at " + mavenVersion);
                    return false;
                }
                getLog().info("  ↻ workspace root — updating Maven wrapper: "
                        + currentVersion + " → " + mavenVersion);
            } else {
                getLog().info("  + workspace root — installing Maven wrapper for Maven "
                        + mavenVersion);
            }

            Files.createDirectories(wrapperDir);

            String props = "# Maven Wrapper properties — managed by ike:init from workspace.yaml\n"
                    + "maven.version=" + mavenVersion + "\n"
                    + "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/"
                    + "apache-maven/" + mavenVersion + "/apache-maven-" + mavenVersion
                    + "-bin.zip\n";
            Files.writeString(propsFile, props, StandardCharsets.UTF_8);

            Path mvnw = root.toPath().resolve("mvnw");
            if (!mvnw.toFile().exists()) {
                writeMvnwScript(mvnw);
            }

            Path mvnwCmd = root.toPath().resolve("mvnw.cmd");
            if (!mvnwCmd.toFile().exists()) {
                writeMvnwCmdScript(mvnwCmd);
            }

            return true;
        } catch (IOException e) {
            getLog().warn("    Could not install workspace root Maven wrapper: " + e.getMessage());
            return false;
        }
    }

    /**
     * Ensure the Maven wrapper is installed and points to the correct version.
     * Writes {@code .mvn/wrapper/maven-wrapper.properties} and the
     * {@code mvnw} / {@code mvnw.cmd} launcher scripts.
     *
     * <p>Skips if no maven-version is configured for this component.
     * Updates the properties file if the version has changed (e.g., after
     * a branch switch updates workspace.yaml).
     *
     * @param componentDir the component root directory
     * @param component    the component definition
     * @param defaults     workspace defaults
     * @return true if wrapper was installed or updated
     */
    private boolean ensureMavenWrapper(File componentDir, Component component,
                                        Defaults defaults) {
        String mavenVersion = resolveMavenVersion(component, defaults);
        if (mavenVersion == null) {
            return false;
        }

        // Only install wrapper if the component has a pom.xml (it's a Maven project)
        File pomFile = new File(componentDir, "pom.xml");
        if (!pomFile.exists()) {
            return false;
        }

        try {
            Path wrapperDir = componentDir.toPath().resolve(".mvn").resolve("wrapper");
            Path propsFile = wrapperDir.resolve("maven-wrapper.properties");

            // Check if already at the correct version
            if (propsFile.toFile().exists()) {
                Properties existing = new Properties();
                try (var reader = Files.newBufferedReader(propsFile, StandardCharsets.UTF_8)) {
                    existing.load(reader);
                }
                String currentVersion = existing.getProperty("maven.version");
                if (mavenVersion.equals(currentVersion)) {
                    getLog().debug("    Maven wrapper already at " + mavenVersion);
                    return false;
                }
                getLog().info("    Updating Maven wrapper: " + currentVersion
                        + " → " + mavenVersion);
            } else {
                getLog().info("    Installing Maven wrapper for Maven " + mavenVersion);
            }

            // Create .mvn/wrapper/ directory
            Files.createDirectories(wrapperDir);

            // Write maven-wrapper.properties
            String props = "# Maven Wrapper properties — managed by ike:init from workspace.yaml\n"
                    + "maven.version=" + mavenVersion + "\n"
                    + "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/"
                    + "apache-maven/" + mavenVersion + "/apache-maven-" + mavenVersion
                    + "-bin.zip\n";
            Files.writeString(propsFile, props, StandardCharsets.UTF_8);

            // Write mvnw launcher script (Unix)
            Path mvnw = componentDir.toPath().resolve("mvnw");
            if (!mvnw.toFile().exists()) {
                writeMvnwScript(mvnw);
            }

            // Write mvnw.cmd launcher script (Windows)
            Path mvnwCmd = componentDir.toPath().resolve("mvnw.cmd");
            if (!mvnwCmd.toFile().exists()) {
                writeMvnwCmdScript(mvnwCmd);
            }

            return true;
        } catch (IOException e) {
            getLog().warn("    Could not install Maven wrapper: " + e.getMessage());
            return false;
        }
    }

    /**
     * Write the Unix mvnw launcher script. This is the standard Maven Wrapper
     * bootstrap script that downloads the correct Maven version on first use.
     */
    private void writeMvnwScript(Path mvnw) throws IOException {
        String script = """
                #!/bin/sh
                # Maven Wrapper launcher — installed by ike:init
                # Downloads and caches the Maven version specified in
                # .mvn/wrapper/maven-wrapper.properties
                #
                # This is a minimal bootstrap. For the full-featured wrapper script,
                # run: mvn wrapper:wrapper

                set -e

                PROPS_FILE="$(dirname "$0")/.mvn/wrapper/maven-wrapper.properties"
                if [ ! -f "$PROPS_FILE" ]; then
                    echo "Error: $PROPS_FILE not found" >&2
                    exit 1
                fi

                DIST_URL=$(grep '^distributionUrl=' "$PROPS_FILE" | cut -d'=' -f2-)
                MAVEN_VERSION=$(grep '^maven.version=' "$PROPS_FILE" | cut -d'=' -f2-)

                WRAPPER_HOME="${HOME}/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}"
                MAVEN_HOME="${WRAPPER_HOME}/apache-maven-${MAVEN_VERSION}"

                if [ ! -d "$MAVEN_HOME" ]; then
                    echo "Downloading Maven ${MAVEN_VERSION}..."
                    mkdir -p "$WRAPPER_HOME"
                    ZIP_FILE="${WRAPPER_HOME}/apache-maven-${MAVEN_VERSION}-bin.zip"
                    curl -fsSL -o "$ZIP_FILE" "$DIST_URL"
                    unzip -qo "$ZIP_FILE" -d "$WRAPPER_HOME"
                    rm -f "$ZIP_FILE"
                    echo "Maven ${MAVEN_VERSION} installed to ${MAVEN_HOME}"
                fi

                exec "$MAVEN_HOME/bin/mvn" "$@"
                """;
        Files.writeString(mvnw, script, StandardCharsets.UTF_8);
        mvnw.toFile().setExecutable(true);
    }

    /**
     * Write the Windows mvnw.cmd launcher script.
     */
    private void writeMvnwCmdScript(Path mvnwCmd) throws IOException {
        String script = """
                @REM Maven Wrapper launcher — installed by ike:init
                @REM Downloads and caches the Maven version specified in
                @REM .mvn/wrapper/maven-wrapper.properties
                @echo off
                setlocal

                set "PROPS_FILE=%~dp0.mvn\\wrapper\\maven-wrapper.properties"
                if not exist "%PROPS_FILE%" (
                    echo Error: %PROPS_FILE% not found >&2
                    exit /b 1
                )

                for /f "tokens=1,* delims==" %%a in ('findstr "^maven.version=" "%PROPS_FILE%"') do set "MAVEN_VERSION=%%b"
                for /f "tokens=1,* delims==" %%a in ('findstr "^distributionUrl=" "%PROPS_FILE%"') do set "DIST_URL=%%b"

                set "WRAPPER_HOME=%USERPROFILE%\\.m2\\wrapper\\dists\\apache-maven-%MAVEN_VERSION%"
                set "MAVEN_HOME=%WRAPPER_HOME%\\apache-maven-%MAVEN_VERSION%"

                if not exist "%MAVEN_HOME%" (
                    echo Downloading Maven %MAVEN_VERSION%...
                    mkdir "%WRAPPER_HOME%" 2>nul
                    set "ZIP_FILE=%WRAPPER_HOME%\\apache-maven-%MAVEN_VERSION%-bin.zip"
                    powershell -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%ZIP_FILE%'"
                    powershell -Command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%WRAPPER_HOME%' -Force"
                    del "%ZIP_FILE%"
                    echo Maven %MAVEN_VERSION% installed to %MAVEN_HOME%
                )

                "%MAVEN_HOME%\\bin\\mvn.cmd" %*
                """;
        Files.writeString(mvnwCmd, script, StandardCharsets.UTF_8);
    }

    /**
     * Ensure {@code .mvn/jvm.config} exists with standard JVM flags.
     * Only applies to Maven projects (must have a {@code pom.xml}).
     * Does not overwrite an existing file.
     */
    private void checkoutSha(File dir, Component component) {
        if (component.sha() == null || component.sha().isBlank()) {
            return;
        }
        try {
            String currentSha = ReleaseSupport.execCapture(dir,
                    "git", "rev-parse", "HEAD");
            if (currentSha.startsWith(component.sha())
                    || component.sha().startsWith(currentSha)) {
                return; // already at the right commit
            }
            getLog().info("    Checking out SHA: " + component.sha().substring(0, 8));
            ReleaseSupport.exec(dir, getLog(),
                    "git", "checkout", component.sha());
        } catch (MojoExecutionException e) {
            getLog().warn("    Could not checkout SHA " + component.sha()
                    + ": " + e.getMessage());
        }
    }

    private void ensureJvmConfig(File componentDir) {
        File pomFile = new File(componentDir, "pom.xml");
        if (!pomFile.exists()) {
            return;
        }

        try {
            Path mvnDir = componentDir.toPath().resolve(".mvn");
            Path jvmConfig = mvnDir.resolve("jvm.config");

            if (jvmConfig.toFile().exists()) {
                return;
            }

            Files.createDirectories(mvnDir);
            String config = "-Dpolyglotimpl.AttachLibraryFailureAction=ignore\n";
            Files.writeString(jvmConfig, config, StandardCharsets.UTF_8);
            getLog().info("    Created .mvn/jvm.config");
        } catch (IOException e) {
            getLog().warn("    Could not create jvm.config: " + e.getMessage());
        }
    }

    /**
     * Write a GOALS.md cheatsheet to the workspace root. Overwrites
     * on each init so it stays current with the installed plugin version.
     */
    private void writeGoalCheatsheet(Path wsRoot) {
        Path goalsFile = wsRoot.resolve("GOALS.md");
        try {
            Files.writeString(goalsFile, generateGoalCheatsheet(),
                    StandardCharsets.UTF_8);
            getLog().info("  Updated GOALS.md");
        } catch (IOException e) {
            getLog().debug("Could not write GOALS.md: " + e.getMessage());
        }
    }

    /**
     * Generate the goal cheatsheet content. Static so it can be tested.
     */
    static String generateGoalCheatsheet() {
        return """
                # Workspace Goals

                All goals are available in IntelliJ's Maven tool window
                under **Plugins > ws** and **Plugins > ike**.

                ## Workspace Management

                | Goal | Description |
                |------|-------------|
                | `ws:create` | Create a new workspace (scaffold + git init) |
                | `ws:add` | Add a component repo (prompts for URL) |
                | `ws:init` | Clone/initialize all components |
                | `ws:fix` | Sync workspace.yaml versions from actual POMs |
                | `ws:graph` | Print dependency graph (text or DOT format) |
                | `ws:stignore` | Generate Syncthing ignore rules |
                | `ws:upgrade-draft` | Preview workspace convention upgrades |
                | `ws:upgrade-publish` | Apply convention upgrades |
                | `ws:remove` | Remove a component (prompts for name) |
                | `ws:help` | List all ws: goals with descriptions |

                ## Verification

                | Goal | Description |
                |------|-------------|
                | `ws:verify` | Check manifest, parents, BOM cascade, VCS state |
                | `ws:verify-convergence` | Full verify + transitive dependency convergence (slow) |
                | `ws:overview` | Workspace overview (manifest, graph, status, cascade) |
                | `ws:cascade` | Show downstream impact of a component change |

                ## Version Alignment

                | Goal | Description |
                |------|-------------|
                | `ws:align-draft` | Preview inter-component version changes |
                | `ws:align-publish` | Apply version alignment to POMs |
                | `ws:pull` | Git pull --rebase across all components |
                | `ws:sync` | Reconcile git state after machine switch |

                ## Feature Branching

                | Goal | Description |
                |------|-------------|
                | `ws:feature-start-draft` | Preview feature branch |
                | `ws:feature-start-publish` | Create feature branch across components |
                | `ws:feature-finish-merge-draft` | Preview no-ff merge |
                | `ws:feature-finish-merge-publish` | No-ff merge (preserves history) |
                | `ws:feature-finish-squash-draft` | Preview squash merge |
                | `ws:feature-finish-squash-publish` | Squash merge (single commit) |
                | `ws:feature-finish-rebase-draft` | Preview rebase |
                | `ws:feature-finish-rebase-publish` | Rebase + fast-forward (linear history) |
                | `ws:feature-abandon-draft` | Preview abandoning a feature branch |
                | `ws:feature-abandon-publish` | Delete feature branch across components |

                ## Release & Checkpoint

                | Goal | Description |
                |------|-------------|
                | `ws:release-draft` | Preview what would be released |
                | `ws:release-publish` | Execute workspace release |
                | `ws:checkpoint-draft` | Preview checkpoint (tag all components) |
                | `ws:checkpoint-publish` | Execute checkpoint |
                | `ws:post-release` | Bump to next development version |
                | `ws:release-notes` | Generate release notes from GitHub milestone |

                ## VCS Bridge (Syncthing multi-machine)

                | Goal | Description |
                |------|-------------|
                | `ws:vcs-sync` | Reconcile state after machine switch |
                | `ws:commit` | Commit with VCS bridge context |
                | `ws:push` | Push with VCS bridge context |

                ## Build Goals (ike:)

                | Goal | Description |
                |------|-------------|
                | `ike:release-draft` | Preview single-repo release |
                | `ike:release-publish` | Execute single-repo release |
                | `ike:generate-bom` | Generate BOM with resolved versions |
                | `ike:deploy-site-draft` | Preview site deployment |
                | `ike:deploy-site-publish` | Deploy project site |
                | `ike:register-site-draft` | Preview org site registration |
                | `ike:register-site-publish` | Register project on org site |
                | `ike:help` | List all ike: goals with descriptions |

                ---
                *Generated by `ws:init`. See `ws:help` and `ike:help` for full details.*
                """;
    }

    // ── WS-REFERENCE.md generation ────────────────────────────────

    private void writeWorkspaceReference(Path wsRoot) {
        Path refFile = wsRoot.resolve("WS-REFERENCE.md");
        try {
            Files.writeString(refFile, generateWorkspaceReference(),
                    StandardCharsets.UTF_8);
            getLog().info("  Updated WS-REFERENCE.md");
        } catch (IOException e) {
            getLog().debug("Could not write WS-REFERENCE.md: " + e.getMessage());
        }
    }

    static String generateWorkspaceReference() {
        return """
                # Workspace Goals Reference

                Complete reference for `ws:` goals. Quick overview: [GOALS.md](GOALS.md).

                ## Convention: -draft / -publish

                Most mutating goals come in pairs:
                - **-draft** (default) — preview mode, no changes made
                - **-publish** — executes the operation

                Example: `mvn ws:feature-start-draft -Dfeature=X` previews,
                `mvn ws:feature-start-publish -Dfeature=X` executes.

                ---

                ## Feature Branching

                ### Start: `ws:feature-start-draft` / `ws:feature-start-publish`

                Create a feature branch, qualify versions (e.g., `1.0.0-SNAPSHOT` becomes
                `1.0.0-CssUtils-SNAPSHOT`), cascade through BOMs and properties.

                | Parameter | Default | Description |
                |-----------|---------|-------------|
                | `feature` | prompted | Feature name (branch: `feature/<name>`) |
                | `group` | all | Restrict to group or component |
                | `targetBranch` | `main` | Source branch |
                | `skipVersion` | `false` | Skip version qualification |

                Fails if any component is on a different feature branch.
                Branches stay local (no auto-push).

                ### Finish: Three Strategies

                **`ws:feature-finish-squash-publish`** (recommended) — single commit on target.
                **`ws:feature-finish-merge-publish`** — no-ff merge, preserves history.
                **`ws:feature-finish-rebase-publish`** — linear history, no merge commit.

                All strategies:
                - Auto-generate commit message from per-component commit history
                - Fail-fast if any component has uncommitted changes
                - Strip branch-qualified versions back to base SNAPSHOT
                - Accept optional `-Dmessage="summary"` prepended to auto-generated message

                | Parameter | Default | Description |
                |-----------|---------|-------------|
                | `feature` | prompted | Feature name |
                | `targetBranch` | `main` | Merge target |
                | `keepBranch` | varies | Keep branch after merge |
                | `message` | auto | Optional human summary |

                ### Abandon: `ws:feature-abandon-draft`

                Delete a feature branch without merging.

                ---

                ## Workspace Lifecycle

                | Goal | Description |
                |------|-------------|
                | `ws:init` | Clone/initialize components from workspace.yaml |
                | `ws:verify` | Check manifest, BOM cascade, VCS state |
                | `ws:verify-convergence` | Full verify + transitive convergence |
                | `ws:overview` | Dashboard: manifest, graph, status, cascade |
                | `ws:fix` | Auto-fix issues found by verify |
                | `ws:upgrade-draft` / `-publish` | Convention upgrades for new plugin version |
                | `ws:pull` | Git pull --rebase across components |

                ---

                ## Release & Checkpoint

                | Goal | Description |
                |------|-------------|
                | `ws:release-draft` / `-publish` | Release modified components in topo order |
                | `ws:checkpoint-draft` / `-publish` | Tag all components, record SHAs |
                | `ws:post-release` | Bump to next SNAPSHOT |
                | `ws:align-draft` / `-publish` | Align inter-component versions |
                | `ws:release-notes` | Generate notes from GitHub milestone |

                ---

                ## VCS Bridge (Syncthing)

                | Goal | Description |
                |------|-------------|
                | `ws:commit` | Commit across repos (`-Dmessage="..." -DaddAll=true -Dpush=true`) |
                | `ws:push` | Push all components to origin |
                | `ws:vcs-sync` | Reconcile after machine switch |

                ---

                ## Troubleshooting

                **Maven discovers `.teamcity/pom.xml`** — Add `-pl !.teamcity` to `.mvn/maven.config`.

                **Feature finish: "uncommitted changes"** — Run `mvn ws:commit -Dmessage="..." -DaddAll=true` first.

                **Feature start: "already on feature branch"** — Finish/abandon the current feature first.

                **Plugin version mismatch** — After upgrading `ike-parent`, run `mvn ws:init`.

                ---
                *Generated by `ws:init`. Regenerated when workspace plugin version changes.*
                """;
    }

    // ── CLAUDE.md generation ─────────────────────────────────────

    /**
     * Write CLAUDE.md at the workspace root. Always overwrites so that
     * standards updates propagate on the next init.
     */
    private void writeWorkspaceClaudeMd(Path wsRoot, WorkspaceGraph graph) {
        Path file = wsRoot.resolve("CLAUDE.md");
        try {
            String wsName = workspaceName();
            Files.writeString(file, generateWorkspaceClaudeMd(wsName, graph),
                    StandardCharsets.UTF_8);
            getLog().info("  Updated CLAUDE.md");
        } catch (IOException e) {
            getLog().debug("Could not write workspace CLAUDE.md: " + e.getMessage());
        }
    }

    /**
     * Write CLAUDE.md in a component directory. Always overwrites.
     */
    private void writeComponentClaudeMd(Path componentDir, Component component) {
        Path file = componentDir.resolve("CLAUDE.md");
        try {
            Files.writeString(file, generateComponentClaudeMd(component),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLog().debug("Could not write CLAUDE.md for " + component.name()
                    + ": " + e.getMessage());
        }
    }

    /**
     * Create CLAUDE-{name}.md template in a directory.
     * Only creates if the file does not already exist — never overwrites.
     * If CLAUDE.md exists but CLAUDE-{name}.md does not, migrates the
     * existing CLAUDE.md content as a starting point.
     */
    private void ensureClaudeNotes(Path dir, String name) {
        Path notesFile = dir.resolve("CLAUDE-" + name + ".md");
        if (Files.exists(notesFile)) {
            return;
        }
        try {
            Path existingClaudeMd = dir.resolve("CLAUDE.md");
            if (Files.exists(existingClaudeMd)) {
                // Migrate existing hand-authored content
                String existing = Files.readString(existingClaudeMd, StandardCharsets.UTF_8);
                String migrated = "# " + name + " — Project Notes\n\n"
                        + "<!-- Migrated from CLAUDE.md by ws:init.\n"
                        + "     This file is for hand-authored, project-specific information.\n"
                        + "     Commit this file to git. -->\n\n"
                        + existing;
                Files.writeString(notesFile, migrated, StandardCharsets.UTF_8);
                getLog().info("    Migrated CLAUDE.md → CLAUDE-" + name + ".md");
            } else {
                Files.writeString(notesFile, generateClaudeNotes(name),
                        StandardCharsets.UTF_8);
                getLog().info("    Created CLAUDE-" + name + ".md (template)");
            }
        } catch (IOException e) {
            getLog().debug("Could not create CLAUDE-" + name + ".md: " + e.getMessage());
        }
    }

    /**
     * Generate CLAUDE.md content for the workspace root. Static for testability.
     */
    static String generateWorkspaceClaudeMd(String wsName, WorkspaceGraph graph) {
        var sb = new StringBuilder();
        sb.append("# ").append(wsName).append("\n\n");

        sb.append("""
                ## First Steps

                Run `mvn ws:init` to clone components, then `mvn validate` to unpack
                full build standards into `.claude/standards/`.

                ## Build

                ```bash
                mvn clean verify -DskipTests -T4   # compile + javadoc
                mvn clean verify -T4                # full build with tests
                ```

                ## Key Conventions

                - Maven 4 with POM modelVersion 4.1.0
                - `<subprojects>` (not `<modules>`) for aggregation
                - All projects use `--enable-preview` (Java 25)
                - Parent: `network.ike:ike-parent` (from ike-pipeline)

                ## Prohibited Patterns

                These are the most critical rules. Full standards are in `.claude/standards/MAVEN.md`
                after building.

                - **Never use `maven-antrun-plugin`** — use a proper Maven goal or `exec-maven-plugin`
                  with an external script
                - **Never use `build-helper-maven-plugin` for multi-execution property chaining** —
                  write a proper Maven goal in `ike-maven-plugin` instead
                - **Never embed shell commands inline in POM** — extract to a named script
                - **Never use `git add -A` or `git add .`** — stage specific files

                ## Project-Specific Notes

                """);

        sb.append("See `WS-REFERENCE.md` for complete workspace goal documentation.\n");
        sb.append("See `CLAUDE-").append(wsName)
                .append(".md` for workspace-specific information.\n");
        sb.append("See `.claude/standards/` (after `mvn validate`) for full build standards.\n");

        return sb.toString();
    }

    /**
     * Generate CLAUDE.md content for a component directory. Static for testability.
     */
    static String generateComponentClaudeMd(Component component) {
        var sb = new StringBuilder();
        sb.append("# ").append(component.name()).append("\n\n");

        if (component.description() != null && !component.description().isBlank()) {
            sb.append(component.description().strip()).append("\n\n");
        }

        sb.append("""
                ## Build Standards

                Files in `.claude/standards/` are build artifacts unpacked from `ike-build-standards`. DO NOT edit or commit them. See the workspace root CLAUDE.md for details.

                ## Build

                ```bash
                mvn clean verify -DskipTests -T4
                ```

                ## Key Facts

                """);

        if (component.groupId() != null) {
            sb.append("- GroupId: `").append(component.groupId()).append("`\n");
        }
        if (component.version() != null) {
            sb.append("- Version: `").append(component.version()).append("`\n");
        }
        sb.append("- Uses `--enable-preview` (Java 25)\n");
        sb.append("- BOM: imports `dev.ikm.ike:ike-bom` for dependency version management\n");

        sb.append("""

                ## Prohibited Patterns

                - **Never use `maven-antrun-plugin`** — use a proper Maven goal or `exec-maven-plugin`
                - **Never use `build-helper-maven-plugin` for multi-execution property chaining** —
                  write a proper Maven goal in `ike-maven-plugin`
                - **Never embed shell commands inline in POM** — extract to a named script

                """);

        sb.append("See `.claude/standards/` (after `mvn validate`) for full standards.\n");
        sb.append("See `CLAUDE-").append(component.name())
                .append(".md` for project-specific notes.\n");

        return sb.toString();
    }

    /**
     * Generate starter template for hand-authored project notes. Static for testability.
     */
    static String generateClaudeNotes(String name) {
        return "# " + name + " — Project Notes\n\n"
                + "<!-- This file is for hand-authored, project-specific information.\n"
                + "     It is created by ws:init but never overwritten.\n"
                + "     Commit this file to git. -->\n\n"
                + "## Architecture\n\n"
                + "## Key Classes\n\n"
                + "## Testing Notes\n";
    }

    /**
     * Install defensive git hooks in the component's .git/hooks/ directory.
     * Skips if the hook already exists (don't overwrite custom hooks).
     */
    private void installHooks(File componentDir) {
        File hooksDir = new File(componentDir, ".git/hooks");
        File postCheckout = new File(hooksDir, "post-checkout");

        if (postCheckout.exists()) {
            getLog().debug("  Hook already exists: " + postCheckout);
            return;
        }

        try {
            if (!hooksDir.exists()) {
                hooksDir.mkdirs();
            }
            String hookScript = "#!/bin/sh\n"
                    + "# Installed by ike:init \u2014 warns on direct branching.\n"
                    + "# Remove this file to disable the check.\n"
                    + "mvn -q ike:check-branch 2>/dev/null\n";
            java.nio.file.Files.writeString(postCheckout.toPath(), hookScript,
                    java.nio.charset.StandardCharsets.UTF_8);
            postCheckout.setExecutable(true);
            getLog().info("    Installed post-checkout hook");
        } catch (java.io.IOException e) {
            getLog().warn("    Could not install hook: " + e.getMessage());
        }
    }
}
