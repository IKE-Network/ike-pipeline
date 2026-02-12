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

## Reactor Build

The reactor aggregator POM (`pipeline/pom.xml`) is a pure aggregator — it is NOT a parent. It lists all modules in dependency order via `<subprojects>`. Module ordering aids readability but Maven sorts automatically by dependency graph.
