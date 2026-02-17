# IKE Build Standards — Claude Standards

## Initial Setup — ALWAYS DO THIS FIRST

This module IS the standards source. The standards files live in
`src/main/standards/`. Read them directly — no unpacking needed.

Read these files in `src/main/standards/`:

- MAVEN.md — Maven 4 build standards (always read)
- IKE-MAVEN.md — IKE-specific Maven conventions (always read)

## Module Overview

This module produces a classified ZIP artifact (`classifier=claude`,
`format=zip`) containing modular Claude instruction files. Consumer
modules unpack this artifact at `validate` phase into `.claude/standards/`
via `maven-dependency-plugin`.

- **Artifact**: `network.ike:ike-build-standards:2:zip:claude`
- **Packaging**: POM (no compiled code)
- **Versioning**: Monotonic integers (1, 2, 3...) — not semver, not SNAPSHOT

## Key Conventions

- Never use SNAPSHOT versions for this artifact
- Increment version by 1 for every release — no major/minor distinction
- The assembly descriptor is `src/assembly/claude-standards.xml`
- Version is managed in `ike-bom`, not in parent POMs

## Build

```bash
mvn install
```
