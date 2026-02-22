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

- `scripts/common-functions.sh` — shared shell utilities (log, git helpers)
- `scripts/ike-workspace.sh` — IKE Workspace management
- `scripts/release-from-feature.sh` — create release branches
- `scripts/merge-to-main.sh` — merge feature to main (placeholder)
- `scripts/create-checkpoint.sh` — create checkpoint (placeholder)
- `scripts/prepare-release.sh` — prepare release (placeholder)
- `scripts/post-release.sh` — post-release version bump (placeholder)
- `scripts/validate-pr.sh` — PR validation (stub)
- `config/.stignore.template` — Syncthing ignore patterns
- `config/checkstyle.xml` — checkstyle rules (stub)
- `config/.editorconfig` — editor settings (stub)

## Build

```bash
mvn install
```
