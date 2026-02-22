# IKE Example Project — Claude Standards

## Initial Setup — ALWAYS DO THIS FIRST

Run `mvn validate` before any other work. This unpacks current build
standards into `.claude/standards/`. Do not proceed without this step.

If `mvn validate` fails because `ike-build-standards` is not in the
local repository, install infrastructure modules first:

```bash
mvn install -pl ike-build-standards -f ../pom.xml
```

After validate completes, read and follow these files in `.claude/standards/`:

- MAVEN.md — Maven 4 build standards (always read)
- IKE-MAVEN.md — IKE-specific Maven conventions (always read)

Read these additional files when working on Java code:

- JAVA.md — Java 25 standards
- IKE-JAVA.md — IKE-specific Java patterns

Read this file when working on AsciiDoc content:

- IKE-DOC.md — Documentation project standards

## Module Overview

Reference implementation demonstrating all IKE pipeline features.
Contains Java source code, comprehensive tests, and AsciiDoc
documentation using diagrams, Koncept macros, and all renderer profiles.

- **Artifact**: `network.ike:example-project`
- **Packaging**: JAR + docs
- **Parent**: `ike-parent`

## Key Build Commands

```bash
# HTML only:
mvn clean verify -Dike.skip.html=false

# Prawn PDF:
mvn clean verify -Dike.pdf.prawn

# FOP PDF:
mvn clean verify -Dike.pdf.fop

# Multiple renderers:
mvn clean verify -Dike.pdf.prawn -Dike.pdf.fop
```

## Output Locations

- HTML: `target/generated-docs/html/index.html`
- PDF: `target/generated-docs/pdf-{renderer}/example-project.pdf`
