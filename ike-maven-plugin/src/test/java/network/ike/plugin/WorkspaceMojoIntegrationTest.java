package network.ike.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for workspace Mojo goals.
 *
 * <p>Each test creates a fresh temp workspace via {@link TestWorkspaceHelper},
 * then instantiates a Mojo directly, sets its {@code manifest} field
 * (package-private in {@link AbstractWorkspaceMojo}), and calls
 * {@link org.apache.maven.plugin.Mojo#execute()}.
 *
 * <p>These Mojos log output via {@code getLog().info()} which defaults
 * to {@code SystemStreamLog} — no mock is needed.
 */
class WorkspaceMojoIntegrationTest {

    @TempDir
    Path tempDir;

    private TestWorkspaceHelper helper;

    @BeforeEach
    void setUp() throws Exception {
        helper = new TestWorkspaceHelper(tempDir);
        helper.buildWorkspace();
    }

    // ── VerifyWorkspaceMojo ─────────────────────────────────────────

    @Test
    void verify_validWorkspace_noException() {
        VerifyWorkspaceMojo mojo = new VerifyWorkspaceMojo();
        mojo.manifest = helper.workspaceYaml().toFile();

        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    // ── CascadeWorkspaceMojo ────────────────────────────────────────

    @Test
    void cascade_fromLeaf_showsDownstream() {
        CascadeWorkspaceMojo mojo = new CascadeWorkspaceMojo();
        mojo.manifest = helper.workspaceYaml().toFile();
        mojo.component = "lib-a";

        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void cascade_fromTip_noDownstream() {
        CascadeWorkspaceMojo mojo = new CascadeWorkspaceMojo();
        mojo.manifest = helper.workspaceYaml().toFile();
        mojo.component = "app-c";

        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    // ── GraphWorkspaceMojo ──────────────────────────────────────────

    @Test
    void graph_textFormat_runsSuccessfully() {
        GraphWorkspaceMojo mojo = new GraphWorkspaceMojo();
        mojo.manifest = helper.workspaceYaml().toFile();
        mojo.format = "text";

        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void graph_dotFormat_runsSuccessfully() {
        GraphWorkspaceMojo mojo = new GraphWorkspaceMojo();
        mojo.manifest = helper.workspaceYaml().toFile();
        mojo.format = "dot";

        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    // ── StatusWorkspaceMojo ─────────────────────────────────────────

    @Test
    void status_allClean_runsSuccessfully() {
        StatusWorkspaceMojo mojo = new StatusWorkspaceMojo();
        mojo.manifest = helper.workspaceYaml().toFile();

        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void status_dirtyRepo_runsSuccessfully() throws Exception {
        // Dirty lib-a by adding an untracked file
        Path untracked = tempDir.resolve("lib-a").resolve("dirty.txt");
        Files.writeString(untracked, "uncommitted", StandardCharsets.UTF_8);

        StatusWorkspaceMojo mojo = new StatusWorkspaceMojo();
        mojo.manifest = helper.workspaceYaml().toFile();

        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void status_groupFilter_runsSuccessfully() {
        StatusWorkspaceMojo mojo = new StatusWorkspaceMojo();
        mojo.manifest = helper.workspaceYaml().toFile();
        mojo.group = "libs";

        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    // ── StignoreWorkspaceMojo ───────────────────────────────────────

    @Test
    void stignore_createsFiles() throws Exception {
        StignoreWorkspaceMojo mojo = new StignoreWorkspaceMojo();
        mojo.manifest = helper.workspaceYaml().toFile();

        mojo.execute();

        // Workspace-level .stignore
        Path wsStignore = tempDir.resolve(".stignore");
        assertThat(wsStignore).exists();
        String wsContent = Files.readString(wsStignore, StandardCharsets.UTF_8);
        assertThat(wsContent).contains("**/target");
        assertThat(wsContent).contains("**/.git");
        assertThat(wsContent).contains("checkpoints");

        // Per-component .stignore files
        for (String name : new String[]{"lib-a", "lib-b", "app-c"}) {
            Path compStignore = tempDir.resolve(name).resolve(".stignore");
            assertThat(compStignore).exists();
            String compContent = Files.readString(compStignore,
                    StandardCharsets.UTF_8);
            assertThat(compContent).contains("**/target");
            assertThat(compContent).contains("**/.git");
        }
    }
}
