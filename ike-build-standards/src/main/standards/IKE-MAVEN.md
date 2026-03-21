# IKE Maven Conventions

## Group ID

All IKE artifacts use `network.ike` as the group ID.

## Parent POM Inheritance

All IKE projects inherit from a single parent:

```
ike-parent                    (root POM — aggregator + parent)
  └── [your-project]          (inherits documentation + Java config)
```

`ike-parent` provides everything:
- AsciiDoc toolchain, PDF renderer profiles, font management, assembly descriptors, skip-flag property pattern
- Java 25 compiler config, JUnit 5 + AssertJ + Mockito dependency management, Surefire/Failsafe configuration
- JPMS support, source/javadoc attachment

The doc pipeline is activated by a file-based profile (`doc-pipeline`).
Projects with `src/docs/asciidoc/` get the full pipeline automatically.
Projects without that directory (infrastructure modules, tool modules)
get only the universal build config (compiler, surefire, enforcer).

## Infrastructure Modules

Infrastructure modules inherit from `ike-parent` like all other modules,
but the doc pipeline does not activate (they lack `src/docs/asciidoc/`):

| Module | Purpose | Packaging |
|---|---|---|
| `ike-build-standards` | Claude instruction files | POM + classified ZIP |
| `ike-build-tools` | Shared build scripts, release automation | POM + classified ZIP |
| `ike-doc-resources` | Shared doc build resources (themes, assembly descriptors, configs) | JAR |
| `minimal-fonts` | Noto font subset for PDF | JAR |
| `docbook-xsl` | DocBook XSL stylesheets + IKE FO layer | JAR |
| `koncept-asciidoc-extension` | AsciidoctorJ inline macro + glossary | JAR |
| `ike-maven-plugin` | Maven plugin wrapping build-tools scripts | maven-plugin |

These are built and installed first in the reactor. The `build-all.sh` script handles this ordering.

## pluginManagement Pattern

All plugin versions are declared in `<pluginManagement>` in the parent POM. Child modules activate plugins by declaring them in `<plugins>` with no version or configuration (unless overriding):

```xml
<!-- Parent: pluginManagement declares version + defaults -->
<pluginManagement>
    <plugins>
        <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>...</configuration>
        </plugin>
    </plugins>
</pluginManagement>

<!-- Child: activates the managed plugin, inherits config -->
<plugins>
    <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
    </plugin>
</plugins>
```

## Dependency Coordination

- Use `<dependencyManagement>` in parent POMs for version alignment.
- Child modules declare dependencies without `<version>` to inherit managed versions.
- Infrastructure module versions are pinned explicitly (they don't inherit from the parent chain).

## Property Naming Convention

IKE-specific properties use the `ike.` prefix:

| Pattern | Purpose | Examples |
|---|---|---|
| `ike.skip.*` | Renderer/feature skip flags | `ike.skip.html`, `ike.skip.prawn` |
| `ike.pdf.*` | PDF renderer activation | `ike.pdf.prawn`, `ike.pdf.fop` |
| `ike.html.*` | HTML variant activation | `ike.html.single` |
| `ike.document.name` | Output document filename | Defaults to `${project.artifactId}` |
| `ike.assembly.directory` | Assembly descriptor location | Path to `src/assembly/` |

## Version Strategy

### Unified Versioning

All modules in the IKE pipeline reactor share a single version.
The version is declared once in the root `pom.xml` `<version>` element.
All subproject modules are versionless (parent is the aggregator).

**Consumer POM workaround (Maven 4.0.0-rc-5):** Maven 4's consumer POM
resolves `${project.version}` to literals in `<dependencies>`, but does
NOT resolve it in `<build><plugins>`, `<pluginManagement>`, or
`<dependencyManagement>`. This means external projects inheriting
`ike-parent` would get unresolved `${project.version}` references for
the `ike-maven-plugin` declaration and managed dependency versions.

The `ike:prepare-release` goal works around this by replacing all
`${project.version}` occurrences with the literal release version in
every POM before deploying, then restoring from backups after deploy.
This ensures deployed consumer POMs contain only concrete versions.

**When to remove:** When Maven resolves `${project.version}` in all
consumer POM sections (not just `<dependencies>`), the backup/replace/
restore logic in `PrepareReleaseMojo` and `ReleaseSupport` can be
removed. Test by deploying without the workaround and checking the
consumer POM (`~/.m2/repository/.../ike-parent-X.pom`) for any
remaining `${project.version}` references. No version indirection
property is needed regardless — use `${project.version}` in source POMs.

- Pipeline versions are sequential: 1.1.0, 1.2.0, 1.3.0, etc.
- No semantic versioning contract is implied. Each release supersedes
  the previous one.
- All modules in the reactor share the unified version.
### Branch-Qualified Versions

Feature branches use a branch-qualified SNAPSHOT version:

    1.2.0-shield-terminology-SNAPSHOT

The qualifier is the branch name with `/` replaced by `-`.
The `main` branch retains the unqualified version:

    1.2.0-SNAPSHOT

The `ike-workspace` script sets this automatically at workspace creation.
See the IKE Workspace Conventions document for the full rationale.

### Standards Artifact Versioning

The `ike-build-standards` artifact now uses the unified pipeline version
(`1.1.0-SNAPSHOT`, etc.) like all other reactor modules. The previous
monotonic integer scheme (1, 2, 3...) is deprecated.

### Release Process

Releases are performed by composable bash scripts in `ike-build-tools`,
not by `maven-release-plugin` or `maven-gitflow-plugin`. A valid release:

1. Sets version to release version (strip `-SNAPSHOT`)
2. Builds and verifies
3. Tags the commit in Git (e.g., `release/1.2.0`)
4. Deploys the release artifact
5. Bumps to the next `-SNAPSHOT` for continued development

**Prohibited**: Manually removing `-SNAPSHOT` from `<version>` elements
and running `mvn deploy`. This produces untagged, unreproducible artifacts.

**During development**, all versions remain `-SNAPSHOT`. Only release
scripts may transition a version to a non-SNAPSHOT release.

### Standards Version Coordination

The `ike-build-standards` version is managed inline in `ike-parent`'s
`<dependencyManagement>` section, along with all other infrastructure
module versions.

- `ike-parent` declares `ike-build-standards` in `<dependencyManagement>`
  with the current version, classifier=claude, type=zip.
- The parent POM's `<pluginManagement>` defines the `unpack-dependencies`
  execution filtered by artifactId and classifier.
- Projects that inherit `ike-parent` get managed versions automatically.
- Standalone modules (not inheriting from the parent chain) declare an
  explicit version in their own dependency.

### Dependency Version Management

All dependency versions are declared inline in `ike-parent`'s
`<dependencyManagement>` section. Projects that inherit `ike-parent`
get managed versions automatically — no separate BOM import is needed.
Child modules declare dependencies without `<version>` to inherit
managed versions from the parent.

## Project-Local Repository (Maven 4)

The IKE pipeline uses Maven 4's split local repository to isolate
installed artifacts per workspace.

In `.mvn/maven.properties`:

    maven.repo.local.path.installed=${session.rootDirectory}/.mvn/local-repo

This ensures that `mvn install` writes artifacts to a directory within
the project tree, not to the shared `~/.m2/repository`. Each IKE Workspace
has its own project-local repository, preventing SNAPSHOT cross-contamination
between branches.

The shared `~/.m2/repository` remains a read-only cache for artifacts
downloaded from remote repositories.

The `.mvn/local-repo/` directory is excluded from:
- Git (via `.gitignore`)
- Syncthing (via `.stignore`)

## POM Element Names

Always use full element names in POM files. Maven 4.1.0 supports
abbreviated element names (e.g., `<n>` for `<name>`, `<v>` for
`<version>`), but these abbreviations harm readability and are not
universally supported by tools. **Never use abbreviated element names.**

| Prohibited | Required |
|---|---|
| `<n>` | `<name>` |
| `<v>` | `<version>` |
| `<g>` | `<groupId>` |
| `<a>` | `<artifactId>` |

## Reactor Build

The root POM (`ike-parent`) is both the reactor aggregator and the single parent POM. It lists all 11 subproject modules via `<subprojects>`. Module ordering aids readability but Maven sorts automatically by dependency graph.
