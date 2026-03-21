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
| `ike-doc-resources` | Shared doc build resources (themes, configs, assembly descriptors) | JAR |
| `minimal-fonts` | Noto font subset for PDF rendering | JAR |
| `docbook-xsl` | DocBook XSL 1.79.2 + IKE FO customization | JAR |
| `koncept-asciidoc-extension` | AsciidoctorJ `k:Name[]` inline macro + glossary | JAR |
| `ike-maven-plugin` | Maven plugin: workspace management, release orchestration, BOM generation | maven-plugin |
| `ike-bom` | Auto-generated BOM (from ike-parent, zero maintenance) | POM |
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
mvn clean verify -Dike.pdf.prawn

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
- Java version: 25 with preview features, uniform across all modules
- 6 PDF renderers: Prawn (free), FOP (free), Prince, AH, WeasyPrint, XEP
- Property-driven build: profiles are thin toggles, all logic in `doc-pipeline` profile
- Output filenames use `${ike.document.name}` (defaults to `${project.artifactId}`)
- Version in root `pom.xml` `<version>` only; all modules are versionless.
  Maven 4's consumer POM only partially resolves `${project.version}` —
  `<dependencies>` yes, but `<build><plugins>` and `<dependencyManagement>` no.
  The `ike:prepare-release` goal works around this (see IKE-MAVEN.md Version Strategy)

## Project-Specific Conventions

- In `<artifactItem>` blocks (maven-dependency-plugin unpack), use
  `${project.version}` for IKE artifact versions — never hardcode.
  The release process replaces `${project.version}` with literals before
  deploying, then restores the originals for the main branch merge.

## Project-Specific Overrides

None. This project follows all shared standards.
