# IKE Build Tools — Claude Standards

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

This module produces a classified ZIP artifact (`classifier=tools`,
`format=zip`) containing shared configuration files. Consumer modules
unpack this artifact at `initialize` phase into `target/build-tools/`.

- **Artifact**: `network.ike:ike-build-tools:zip:tools`
- **Packaging**: POM (no compiled code)

## Contents

- `config/.stignore.template` — Syncthing ignore patterns
- `config/checkstyle.xml` — checkstyle rules
- `config/.editorconfig` — editor settings

All build scripts have been replaced by cross-platform `ike-maven-plugin`
goals (`ike:release`, `ike:checkpoint`, `ike:ws-release`, `ike:feature-start`,
`ike:feature-finish`, `ike:copy-docs`, `ike:fix-svg`, `ike:patch-docbook`,
`ike:scan-logs`, etc.).

## Build

```bash
mvn install
```
