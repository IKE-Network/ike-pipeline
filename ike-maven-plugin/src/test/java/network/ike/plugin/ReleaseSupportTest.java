package network.ike.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for pure functions in {@link ReleaseSupport}:
 * version derivation, path validation, site path generation,
 * and branch-to-path conversion.
 */
class ReleaseSupportTest {

    // ── deriveReleaseVersion ─────────────────────────────────────────

    @Test
    void deriveReleaseVersion_stripsSnapshot() {
        assertThat(ReleaseSupport.deriveReleaseVersion("2-SNAPSHOT"))
                .isEqualTo("2");
    }

    @Test
    void deriveReleaseVersion_dotted() {
        assertThat(ReleaseSupport.deriveReleaseVersion("1.1.0-SNAPSHOT"))
                .isEqualTo("1.1.0");
    }

    @Test
    void deriveReleaseVersion_noSnapshot_unchanged() {
        assertThat(ReleaseSupport.deriveReleaseVersion("3.0.0"))
                .isEqualTo("3.0.0");
    }

    // ── deriveNextSnapshot ───────────────────────────────────────────

    @Test
    void deriveNextSnapshot_simpleInteger() {
        assertThat(ReleaseSupport.deriveNextSnapshot("2"))
                .isEqualTo("3-SNAPSHOT");
    }

    @Test
    void deriveNextSnapshot_dotted() {
        assertThat(ReleaseSupport.deriveNextSnapshot("1.1.0"))
                .isEqualTo("1.1.1-SNAPSHOT");
    }

    @Test
    void deriveNextSnapshot_alreadySnapshot_stillWorks() {
        assertThat(ReleaseSupport.deriveNextSnapshot("1.0.0-SNAPSHOT"))
                .isEqualTo("1.0.1-SNAPSHOT");
    }

    // ── validateRemotePath ───────────────────────────────────────────

    @Test
    void validateRemotePath_validPath_noException() throws MojoExecutionException {
        // Should not throw — path has base + project + type
        ReleaseSupport.validateRemotePath("/srv/ike-site/ike-pipeline/snapshot");
    }

    @Test
    void validateRemotePath_deepPath_noException() throws MojoExecutionException {
        ReleaseSupport.validateRemotePath("/srv/ike-site/ike-pipeline/snapshot/main");
    }

    @Test
    void validateRemotePath_wrongBase_throws() {
        assertThatThrownBy(() ->
                ReleaseSupport.validateRemotePath("/tmp/evil/path"))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("does not start with");
    }

    @Test
    void validateRemotePath_tooShallow_throws() {
        assertThatThrownBy(() ->
                ReleaseSupport.validateRemotePath("/srv/ike-site/"))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("too shallow");
    }

    @Test
    void validateRemotePath_justBase_throws() {
        assertThatThrownBy(() ->
                ReleaseSupport.validateRemotePath("/srv/ike-site/"))
                .isInstanceOf(MojoExecutionException.class);
    }

    // ── siteDiskPath ─────────────────────────────────────────────────

    @Test
    void siteDiskPath_release() {
        assertThat(ReleaseSupport.siteDiskPath("ike-pipeline", "release", null))
                .isEqualTo("/srv/ike-site/ike-pipeline/release");
    }

    @Test
    void siteDiskPath_snapshotWithBranch() {
        assertThat(ReleaseSupport.siteDiskPath("ike-pipeline", "snapshot", "main"))
                .isEqualTo("/srv/ike-site/ike-pipeline/snapshot/main");
    }

    @Test
    void siteDiskPath_checkpoint() {
        assertThat(ReleaseSupport.siteDiskPath("ike-docs", "checkpoint", "v1.0"))
                .isEqualTo("/srv/ike-site/ike-docs/checkpoint/v1.0");
    }

    @Test
    void siteDiskPath_blankSubPath_noTrailingSlash() {
        assertThat(ReleaseSupport.siteDiskPath("proj", "release", ""))
                .isEqualTo("/srv/ike-site/proj/release");
    }

    // ── branchToSitePath ─────────────────────────────────────────────

    @Test
    void branchToSitePath_main_unchanged() {
        assertThat(ReleaseSupport.branchToSitePath("main"))
                .isEqualTo("main");
    }

    @Test
    void branchToSitePath_featureBranch_preservesSlash() {
        assertThat(ReleaseSupport.branchToSitePath("feature/my-work"))
                .isEqualTo("feature/my-work");
    }

    @Test
    void branchToSitePath_unsafeChars_replaced() {
        assertThat(ReleaseSupport.branchToSitePath("feature/weird@chars!"))
                .isEqualTo("feature/weird-chars-");
    }

    // ── siteStagingPath ──────────────────────────────────────────────

    @Test
    void siteStagingPath_appendsSuffix() {
        assertThat(ReleaseSupport.siteStagingPath("/srv/ike-site/proj/release"))
                .isEqualTo("/srv/ike-site/proj/release.staging");
    }
}
