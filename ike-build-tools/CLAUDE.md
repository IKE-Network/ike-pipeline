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
`format=zip`) containing shared build scripts, release automation,
and configuration files. Consumer modules unpack this artifact at
`initialize` phase into `target/build-tools/`.

- **Artifact**: `network.ike:ike-build-tools:1.1.0-SNAPSHOT:zip:tools`
- **Packaging**: POM (no compiled code)

## Contents

- `scripts/fix-inline-svg.sh` — SVG post-processing for PDF renderers
- `scripts/patch-docbook-xsl.sh` — DocBook XSL patching
- `scripts/scan-renderer-logs.sh` — renderer log analysis
- `scripts/copy-docs-to-site.sh` — copy generated docs to site directory

NOTE: Workspace and release scripts (`ike-workspace.sh`, `merge-to-main.sh`,
`create-checkpoint.sh`, `prepare-release.sh`, `post-release.sh`,
`release-from-feature.sh`, `validate-pr.sh`, `common-functions.sh`)
have been replaced by `ike-maven-plugin` goals (`ike:init`, `ike:status`,
`ike:dashboard`, `ike:feature-finish`, `ike:ws-checkpoint`, `ike:release`,
`ike:verify`).
- `config/.stignore.template` — Syncthing ignore patterns
- `config/checkstyle.xml` — checkstyle rules (stub)
- `config/.editorconfig` — editor settings (stub)

## Build

```bash
mvn install
```
