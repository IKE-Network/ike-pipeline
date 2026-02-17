# IKE Documentation Example

A documentation-only project that demonstrates the IKE AsciiDoc pipeline.
Use this as a template for creating new documentation projects.

## Quick Start

```bash
# From the reactor root, install infrastructure first:
mvn clean install -pl '!example-project,!doc-example'

# Then build this module:
mvn clean verify -pl doc-example -Dike.pdf.prawn
```

## Creating a New Documentation Project

### 1. Create `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0
         http://maven.apache.org/xsd/maven-4.1.0.xsd">
    <modelVersion>4.1.0</modelVersion>

    <parent>
        <groupId>network.ike</groupId>
        <artifactId>ike-parent</artifactId>
        <version>1.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>my-docs</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <name>My Documentation Project</name>

    <dependencies>
        <dependency>
            <groupId>network.ike</groupId>
            <artifactId>ike-build-standards</artifactId>
            <classifier>claude</classifier>
            <type>zip</type>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>network.ike</groupId>
            <artifactId>minimal-fonts</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <executions>
                    <execution>
                        <id>unpack-doc-dependencies</id>
                        <phase>generate-sources</phase>
                        <goals><goal>unpack-dependencies</goal></goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.asciidoctor</groupId>
                <artifactId>asciidoctor-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 2. Create `src/docs/asciidoc/index.adoc`

```asciidoc
= My Document Title
:author: Your Name
:revnumber: {project-version}
:revdate: {docdate}
:doctype: book
:toc: left
:toclevels: 3
:sectnums:
:icons: font
:source-highlighter: coderay

== First Chapter

Your content here.
```

### 3. Build

```bash
# HTML only:
mvn clean verify

# HTML + Prawn PDF:
mvn clean verify -Dike.pdf.prawn

# HTML + FOP PDF:
mvn clean verify -Dike.pdf.fop
```

### 4. Customize the Theme

To override the default IKE theme, create `src/theme/ike-default-theme.yml`
in your project and add this property to your `pom.xml`:

```xml
<properties>
    <asciidoc.theme.directory>${project.basedir}/src/theme</asciidoc.theme.directory>
</properties>
```

### 5. Add Diagrams

The pipeline supports Mermaid, PlantUML, and GraphViz via Kroki
server-side rendering. No local tools needed.

```asciidoc
[mermaid]
....
graph LR
    A[Source] --> B[Build] --> C[Output]
....
```

### 6. Use Koncept Macros

Reference formally identified terminology with `k:Name[]`:

```asciidoc
The Koncept k:DiabetesMellitus[] is a metabolic disorder.
```

### 7. Standalone Repository

For a project in its own repository (outside the reactor), ensure these
artifacts are installed in your local Maven repository or deployed to
a shared repository:

- `network.ike:ike-parent`
- `network.ike:ike-doc-resources`
- `network.ike:ike-build-standards`
- `network.ike:minimal-fonts`
- `network.ike:koncept-asciidoc-extension`

## Output Locations

| Format | Path |
|--------|------|
| HTML | `target/generated-docs/html/index.html` |
| Self-contained HTML | `target/generated-docs/html-single/{artifactId}.html` |
| Prawn PDF | `target/generated-docs/pdf-prawn/{artifactId}.pdf` |
| FOP PDF | `target/generated-docs/pdf-fop/{artifactId}.pdf` |
| Prince PDF | `target/generated-docs/pdf-prince/{artifactId}.pdf` |
| AH PDF | `target/generated-docs/pdf-ah/{artifactId}.pdf` |
| WeasyPrint PDF | `target/generated-docs/pdf-weasyprint/{artifactId}.pdf` |
| XEP PDF | `target/generated-docs/pdf-xep/{artifactId}.pdf` |
