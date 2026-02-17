# IKE Documentation Example — Claude Standards

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
- IKE-DOC.md — Documentation project standards (always read)

Do NOT read JAVA.md or IKE-JAVA.md — this is a doc-only project with
no Java source code.

## Module Overview

Documentation-only project that inherits from `ike-parent` directly
(not `java-parent`). Exercises all pipeline features across all 6 PDF
renderers: diagrams, Koncept macros, typography, tables, and layout.

- **Artifact**: `network.ike:doc-example`
- **Packaging**: JAR (empty JAR — doc-only projects use `jar` packaging
  to inherit the full renderer pipeline)
- **Parent**: `ike-parent`

## Key Build Commands

```bash
# HTML only:
mvn clean verify

# Prawn PDF:
mvn clean verify -Dike.pdf.prawn

# FOP PDF:
mvn clean verify -Dike.pdf.fop

# Multiple renderers:
mvn clean verify -Dike.pdf.prawn -Dike.pdf.fop
```

## Output Locations

- HTML: `target/generated-docs/html/index.html`
- PDF: `target/generated-docs/pdf-{renderer}/doc-example.pdf`
