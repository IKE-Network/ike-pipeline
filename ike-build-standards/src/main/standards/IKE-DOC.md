# IKE Documentation Project Standards

## Parent Selection

- **Doc-only projects**: inherit from `ike-parent`
- **Java + docs projects**: inherit from `java-parent` (which inherits `ike-parent`)

## Packaging

Doc-only projects use `jar` packaging, even though they produce no Java classes.
This avoids the `pom-skip-renderers` profile (which disables all renderers for
POM-packaging modules). The JAR artifact is minimal (only `META-INF/MANIFEST.MF`).

## Required Directory Structure

Minimum for a documentation project:

```
my-project/
├── pom.xml
└── src/
    └── docs/
        └── asciidoc/
            └── index.adoc
```

Optional additions:

```
src/docs/asciidoc/
├── index.adoc              # Master document
├── chapters/               # Modular chapter includes
│   ├── intro.adoc
│   └── architecture.adoc
└── .mermaid-config.json    # Mermaid diagram config
```

## Document Attributes

Standard attributes for the master document (`index.adoc`):

```asciidoc
= Document Title
:author: IKE Community
:revnumber: {project-version}
:revdate: {docdate}
:doctype: book
:toc: left
:toclevels: 3
:sectnums:
:icons: font
:source-highlighter: coderay
:experimental:
```

## Diagram Conventions

All diagrams are rendered server-side via Kroki. No local CLI tools needed.

- **Mermaid**: Use `htmlLabels: false` in `.mermaid-config.json` for PDF compatibility
- **PlantUML**: Standard `@startuml`/`@enduml` syntax
- **GraphViz**: Standard `digraph`/`graph` syntax
- **Kroki server**: Default `https://kroki.komet.sh`, override with `-Dkroki.server.url=...`

## Koncept Macro Usage

Reference formally identified terminology with `k:Name[]`:

```asciidoc
The Koncept k:DiabetesMellitus[] is a metabolic disorder.
```

Koncept definitions are provided via YAML files in the
`koncept-asciidoc-extension` module.

## Theme Customization

The default IKE theme is provided by `ike-doc-resources` and unpacked
automatically. To override:

1. Create `src/theme/ike-default-theme.yml` in your project
2. Add to `<properties>` in your POM:

```xml
<asciidoc.theme.directory>${project.basedir}/src/theme</asciidoc.theme.directory>
```

## Build Commands

```bash
# HTML only (default):
mvn clean verify

# HTML + specific PDF renderer:
mvn clean verify -Dike.pdf.prawn
mvn clean verify -Dike.pdf.fop
mvn clean verify -Dike.pdf.prince
mvn clean verify -Dike.pdf.ah
mvn clean verify -Dike.pdf.weasyprint
mvn clean verify -Dike.pdf.xep

# Multiple renderers:
mvn clean verify -Dike.pdf.prawn -Dike.pdf.fop

# Self-contained HTML:
mvn clean verify -Dike.html.single
```

## Output Locations

| Format | Directory |
|--------|-----------|
| HTML | `target/generated-docs/html/` |
| Self-contained HTML | `target/generated-docs/html-single/` |
| Prawn PDF | `target/generated-docs/pdf-prawn/` |
| FOP PDF | `target/generated-docs/pdf-fop/` |
| Prince PDF | `target/generated-docs/pdf-prince/` |
| AH PDF | `target/generated-docs/pdf-ah/` |
| WeasyPrint PDF | `target/generated-docs/pdf-weasyprint/` |
| XEP PDF | `target/generated-docs/pdf-xep/` |
| Default PDF copy | `target/generated-docs/pdf/` |

## Creating a Standalone Doc Project

For a doc project in its own repository (outside the IKE reactor):

1. Ensure these artifacts are deployed to a shared repository:
   - `network.ike:ike-parent`
   - `network.ike:ike-doc-resources`
   - `network.ike:ike-build-standards`
   - `network.ike:minimal-fonts`
   - `network.ike:koncept-asciidoc-extension`
   - `network.ike:ike-bom`

2. Use the POM template from `doc-example/README.md`

3. The `ike-doc-resources` JAR is unpacked automatically by `ike-parent`'s
   `maven-dependency-plugin` configuration — no `../` paths needed.

## Cross-Project Doc Inclusion

To include documentation from another project:

1. The source project packages AsciiDoc as a classified ZIP:
   ```xml
   <classifier>asciidoc</classifier>
   <type>zip</type>
   ```

2. The consumer declares it as a dependency and unpacks via
   `maven-dependency-plugin`'s `unpack-dependencies` goal.

3. Reference via the `{generated}` attribute:
   ```asciidoc
   include::{generated}/other-project/chapters/shared.adoc[]
   ```
