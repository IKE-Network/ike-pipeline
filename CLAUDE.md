# IKE Documentation Pipeline — Claude Standards

## Initial Setup — ALWAYS DO THIS FIRST

Run `mvn validate` before any other work. This unpacks current build
standards into `.claude/standards/` for each module. Do not proceed
without this step.

If `mvn validate` fails because `ike-build-standards` is not in the
local repository, install infrastructure modules first:

```bash
mvn install -pl ike-build-standards
```

After validate completes, read and follow these files in `.claude/standards/`:

- MAVEN.md — Maven 4 build standards (always read)
- IKE-MAVEN.md — IKE-specific Maven conventions (always read)

Read these additional files when working on Java code:

- JAVA.md — Java 25 standards
- IKE-JAVA.md — IKE-specific Java patterns (RocksDB, gRPC, Koncept extension)

Do not read other files in that directory unless specifically relevant
to a task you are performing.

## Project Overview

This is the IKE Documentation Pipeline — a Maven 4 reactor that provides
a multi-renderer AsciiDoc-to-PDF/HTML pipeline for IKE Community projects.

The root POM (`ike-parent`) serves as both the reactor aggregator and
the single parent POM for all modules. All 11 subproject modules are
versionless (parent is the aggregator).

### Module Structure

| Module | Purpose | Packaging |
|---|---|---|
| `ike-build-tools` | Shared build scripts + release automation | POM + ZIP |
| `ike-build-standards` | Versioned Claude instruction files | POM + ZIP |
| `ike-bom` | Bill of Materials for centralized dependency versions | POM |
| `ike-doc-resources` | Shared doc build resources (themes, configs, assembly descriptors) | JAR |
| `minimal-fonts` | Noto font subset for PDF rendering | JAR |
| `docbook-xsl` | DocBook XSL 1.79.2 + IKE FO customization | JAR |
| `koncept-asciidoc-extension` | AsciidoctorJ `k:Name[]` inline macro + glossary | JAR |
| `ike-maven-plugin` | Maven plugin wrapping build-tools bash scripts | maven-plugin |
| `semantic-linebreak` | CLI tool — AsciiDoc semantic linefeed reformatter | JAR |
| `doc-example` | Doc-only project exercising all pipeline features | JAR (empty) |
| `example-project` | Java+docs demo project | JAR + docs |

### Parent Architecture

All modules inherit directly from `ike-parent` (the root POM). There
is no intermediate parent hierarchy.

The doc pipeline (~50 plugin executions for 6 PDF renderers) is
activated by a file-based profile:

```xml
<profile>
    <id>doc-pipeline</id>
    <activation>
        <file><exists>src/docs/asciidoc</exists></file>
    </activation>
</profile>
```

- Infrastructure modules lack `src/docs/asciidoc` → no doc pipeline
- Doc projects have it → full pipeline automatically
- External consumers inherit the same behavior

### Key Build Commands

```bash
# Full reactor build (HTML + Prawn PDF):
./build-all.sh --pdf=prawn

# Single module, specific renderer:
mvn clean verify -pl example-project -Dike.pdf.xep

# Multiple renderers:
mvn clean verify -pl example-project -Dike.pdf.prawn -Dike.pdf.fop

# HTML only:
mvn clean verify -pl example-project -Dike.skip.html=false
```

## Project-Specific Context

- Group ID: `network.ike`
- Model version: `4.1.0` for all POMs
- Java baseline: 21+ (JRuby 10 requirement)
- Java version: 25 (default), overridden by koncept-asciidoc-extension (17)
  and ike-maven-plugin (21)
- 6 PDF renderers: Prawn (free), FOP (free), Prince, AH, WeasyPrint, XEP
- Property-driven build: profiles are thin toggles, all logic in `doc-pipeline` profile
- Output filenames use `${ike.document.name}` (defaults to `${project.artifactId}`)
- Version in exactly 2 places in root `pom.xml`: `<version>` and `<ike.pipeline.version>`

## Project-Specific Overrides

None. This project follows all shared standards.
