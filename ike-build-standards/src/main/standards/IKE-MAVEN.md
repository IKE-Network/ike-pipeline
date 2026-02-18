# IKE Maven Conventions

## Group ID

All IKE artifacts use `network.ike` as the group ID.

## Parent POM Inheritance

The parent chain for IKE projects:

```
ike-parent                    (documentation pipeline, renderer profiles)
  └── java-parent             (Java compiler, test frameworks, JPMS support)
       └── [your-project]     (inherits both documentation and Java config)
```

- `ike-parent` provides: AsciiDoc toolchain, PDF renderer profiles, font management, assembly descriptors, skip-flag property pattern.
- `java-parent` provides: Java 25 compiler config, JUnit 5 + AssertJ + Mockito dependency management, Surefire/Failsafe configuration, source/javadoc attachment.

Projects that need only documentation (no Java code) inherit directly from `ike-parent` (see `doc-example`). Projects with Java code inherit from `java-parent` (see `example-project`).

## Infrastructure Modules

Infrastructure modules are standalone (no parent inheritance):

| Module | Purpose | Packaging |
|---|---|---|
| `ike-build-standards` | Claude instruction files | POM + classified ZIP |
| `ike-build-tools` | Shared build scripts, release automation | POM + classified ZIP |
| `ike-doc-resources` | Shared doc build resources (themes, assembly descriptors, configs) | JAR |
| `minimal-fonts` | Noto font subset for PDF | JAR |
| `docbook-xsl` | DocBook XSL stylesheets + IKE FO layer | JAR |
| `koncept-asciidoc-extension` | AsciidoctorJ inline macro + glossary | JAR |

These are built and installed first (`mvn install`) before the main project build. The `build-all.sh` script handles this ordering.

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

- All modules use `-SNAPSHOT` versions during development.
- Infrastructure modules have independent version tracks (e.g.,
  `minimal-fonts:1.0.0-SNAPSHOT`, `docbook-xsl:1.79.2-ike.2-SNAPSHOT`).
- Parent POMs and project modules use coordinated SNAPSHOT versions
  (e.g., `1.1.0-SNAPSHOT`).

### Release Process — NEVER manually edit versions

**Releases MUST use a proper release tool** — either the
`maven-release-plugin`, the `gitflow-maven-plugin`, or an equivalent
SCM-integrated release process. A valid release:

1. Is performed by a release plugin (not manual version edits)
2. Removes `-SNAPSHOT`, builds, deploys the release artifact
3. Tags the commit in SCM (e.g., `v1.0.0`)
4. Bumps to the next `-SNAPSHOT` for continued development
5. Pushes both the tag and the version bump commit

**Prohibited**: Manually removing `-SNAPSHOT` from `<version>` elements
and running `mvn deploy`. This produces untagged, unreproducible artifacts
in the release repository. The Nexus release repository rejects
redeployment, so a botched manual release permanently burns that version
number.

**During development**, all versions remain `-SNAPSHOT`. Only a release
plugin may transition a version to a non-SNAPSHOT release.

### Standards Artifact Versioning

The `ike-build-standards` artifact uses **monotonic integer versioning**: 1, 2, 3, 4, etc. No dots, no semantic versioning, no calver. These are build standards documents, not a library API — there is no compatibility contract to encode in the version number.

- Increment by 1 for every release. No major/minor/patch distinction.
- Do NOT use SNAPSHOT versions for this artifact. It must be a release version.
- Do NOT use semantic versioning. If you find yourself wondering whether a standards change is "major" or "minor," you are overthinking it. Increment by 1.

### Standards Version Coordination

The `ike-build-standards` version is managed in `ike-bom`, not in the parent POM. This keeps the parent POM stable.

- The BOM declares `ike-build-standards` in `<dependencyManagement>` with the current version, classifier=claude, type=zip.
- The parent POM's `<pluginManagement>` defines the `unpack-dependencies` execution filtered by artifactId and classifier. No version appears in the parent POM.
- Child Java projects declare `ike-build-standards` as a `<dependency>` with `scope=provided` (version resolved from BOM). Then activate the dependency plugin from pluginManagement.
- Non-BOM projects (standalone modules not inheriting from the parent chain) declare an explicit version in their own dependency.
- Bumping standards version = one property change in the BOM, included in the next BOM release. The parent POM does not change.

### IKE BOM

The `ike-bom` artifact (`network.ike:ike-bom`) provides centralized version management. Import it in `<dependencyManagement>`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>network.ike</groupId>
            <artifactId>ike-bom</artifactId>
            <version>${ike-bom.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Child modules then declare dependencies without `<version>` — versions are resolved from the BOM.

## Reactor Build

The reactor aggregator POM (`pipeline/pom.xml`) is a pure aggregator — it is NOT a parent. It lists all modules in dependency order via `<subprojects>`. Module ordering aids readability but Maven sorts automatically by dependency graph.
