# IKE Parent — Claude Standards

## Initial Setup — ALWAYS DO THIS FIRST

Run `mvn validate` before any other work. This unpacks current build
standards into `.claude/standards/`. Do not proceed without this step.

If `mvn validate` fails because `ike-build-standards` is not in the
local repository, install it first:

```bash
mvn install -f ../ike-build-standards/pom.xml
```

After validate completes, read and follow these files in `.claude/standards/`:

- MAVEN.md — Maven 4 build standards (always read)
- IKE-MAVEN.md — IKE-specific Maven conventions (always read)

## Module Overview

Root POM serving as both the reactor aggregator and the single parent
for all IKE modules. Provides centralized dependency version management
inline in `<dependencyManagement>`, build plugin configuration, and the
AsciiDoc documentation pipeline.

- **Artifact**: `network.ike:ike-pipeline` (ike-parent)
- **Packaging**: POM

## Key Conventions

- Dependency versions are managed inline in `<dependencyManagement>`
- All modules in the reactor share the unified pipeline version
- Projects inheriting ike-parent get managed versions automatically

## Build

```bash
mvn install
```
