# IKE Maven Conventions

## Group ID

All IKE artifacts use `network.ike` as the group ID.

## Parent POM Inheritance

The parent chain for IKE projects:

```
documentation-parent          (documentation pipeline, renderer profiles)
  └── java-parent             (Java compiler, test frameworks, JPMS support)
       └── [your-project]     (inherits both documentation and Java config)
```

- `documentation-parent` provides: AsciiDoc toolchain, PDF renderer profiles, font management, assembly descriptors, skip-flag property pattern.
- `java-parent` provides: Java 25 compiler config, JUnit 5 + AssertJ + Mockito dependency management, Surefire/Failsafe configuration, source/javadoc attachment.

Projects that need only documentation (no Java code) inherit directly from `documentation-parent`. Projects with Java code inherit from `java-parent`.

## Infrastructure Modules

Infrastructure modules are standalone (no parent inheritance):

| Module | Purpose | Packaging |
|---|---|---|
| `ike-build-standards` | Claude instruction files | POM + classified ZIP |
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

- Infrastructure modules: independent versions (e.g., `minimal-fonts:1.0.0`, `docbook-xsl:1.79.2-ike.2`).
- Parent POMs and project modules: coordinated SNAPSHOT versions during development (e.g., `1.1.0-SNAPSHOT`).
- Release versions: remove `-SNAPSHOT` suffix, tag, deploy, then bump to next SNAPSHOT.

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
