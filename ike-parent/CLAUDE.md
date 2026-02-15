# IKE Parent — Claude Standards

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

## Module Overview

Parent POM for all IKE documentation projects. Provides the AsciiDoc
toolchain, PDF renderer profiles, font management, assembly descriptors,
and the property-driven skip-flag build pattern.

- **Artifact**: `network.ike:ike-parent`
- **Packaging**: POM
- **Children**: `java-parent` inherits from this; `example-project`
  inherits from `java-parent`

## Key Conventions

- Property-driven build: all logic in main `<build>`, profiles are thin
  toggles that flip `ike.skip.*` flags
- 6 PDF renderers: Prawn, FOP, Prince, AH, WeasyPrint, XEP
- Path properties (`ike.extensions.directory`, `ike.shared.asciidoc.directory`,
  etc.) use `../ike-parent/` relative paths for reactor builds
- Assembly descriptors in `src/assembly/`
- Shared docinfo in `src/shared-asciidoc/`
- Print CSS theme in `src/theme/`
- FOP/XEP configs in `src/fop/` and `src/xep/`
- Output filenames use `${ike.document.name}` (defaults to `${project.artifactId}`)

## Build

```bash
# Install this POM to local repo:
mvn install -N

# Build with a specific renderer (from reactor root):
mvn clean verify -pl example-project -Dike.pdf.prawn
```
