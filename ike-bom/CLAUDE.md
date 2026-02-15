# IKE BOM — Claude Standards

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

Bill of Materials (BOM) POM providing centralized version management
for all IKE artifacts. Other projects import this BOM in their
`<dependencyManagement>` to align dependency versions.

- **Artifact**: `network.ike:ike-bom`
- **Packaging**: POM

## Key Conventions

- This BOM manages the `ike-build-standards` version (monotonic integer)
- Bumping the standards version = one property change here
- Do not add plugin configuration to this POM — it is purely for
  dependency version alignment

## Build

```bash
mvn install
```
