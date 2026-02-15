# IKE Java Parent — Claude Standards

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

## Module Overview

Parent POM for IKE Java projects. Extends `ike-parent` with Java 25
compiler configuration, test framework setup (JUnit 5, AssertJ, Mockito),
Surefire/Failsafe configuration, and JPMS support.

- **Artifact**: `network.ike:java-parent`
- **Packaging**: POM
- **Parent**: `ike-parent`
- **Children**: `example-project` inherits from this

## Key Conventions

- Java 25 with preview features enabled
- JPMS support via `-Pjpms` profile
- Release profile attaches sources + javadoc JARs
- Test frameworks pre-configured in `<dependencyManagement>`

## Build

```bash
# Install this POM to local repo:
mvn install -N
```
