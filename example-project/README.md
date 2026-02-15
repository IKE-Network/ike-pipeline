# IKE Example Project

Demonstration project showcasing the IKE Community Maven AsciiDoc pipeline with Java 25 development.

## Features

- **Java 25** with modern records and language features
- **Comprehensive Testing** using JUnit 5, AssertJ, and Mockito
- **Professional Documentation** with AsciiDoc (HTML + PDF output)
- **Diagram Support** including Mermaid, PlantUML, and GraphViz
- **Maven 4.1.0** with latest build conventions
- **IDE Integration** with IntelliJ IDEA pre-configured

## Quick Start

### Prerequisites

- **JDK 25** (or compatible)
- **Maven 4.0.0+** (wrapper included)
- **Diagram Tools** (optional, for diagram rendering):
  - GraphViz: `brew install graphviz` (macOS) or `apt install graphviz` (Linux)
  - Mermaid CLI: `npm install -g @mermaid-js/mermaid-cli`

### Build and Test

```bash
# Build with HTML documentation (default)
./mvnw clean verify

# Build with PDF documentation
./mvnw clean verify -Ppdf

# Build HTML + PDF (Prawn)
./mvnw clean verify -Dike.pdf.prawn

# Run tests only
./mvnw test
```

### View Documentation

After building:

- **HTML**: `target/generated-docs/html/index.html`
- **PDF**: `target/generated-docs/pdf/index.pdf`

## Project Structure

```
example-project/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── org/ike/example/
│   │           └── Concept.java          # SNOMED CT-like concept
│   ├── test/
│   │   └── java/
│   │       └── org/ike/example/
│   │           └── ConceptTest.java      # Comprehensive tests
│   └── docs/
│       └── asciidoc/
│           ├── index.adoc                # Main documentation
│           └── chapters/                 # Modular chapters
│               ├── architecture.adoc
│               ├── concept-model.adoc
│               └── diagrams.adoc
├── pom.xml                               # Maven configuration
└── .idea/
    └── asciidoc.xml                      # IntelliJ AsciiDoc config
```

## Key Concepts

### Concept Class

The `Concept` record demonstrates SNOMED CT principles:

```java
Concept diabetes = new Concept(
    73211009L,
    "Diabetes mellitus (disorder)",
    "Diabetes mellitus",
    "A metabolic disorder characterized by high blood glucose levels"
);

// Check if primitive (incomplete definition)
boolean isPrimitive = diabetes.isPrimitive();

// Factory method with semantic tag
Concept finding = Concept.clinicalFinding(
    38341003L,
    "Hypertension",
    "High blood pressure",
    "Elevated arterial blood pressure"
);
```

### Testing

Comprehensive test coverage using:

- **JUnit 5**: Modern testing framework
- **AssertJ**: Fluent assertions
- **Parameterized Tests**: Data-driven testing

```java
@Test
@DisplayName("Should create valid concept")
void shouldCreateValidConcept() {
    Concept concept = new Concept(123L, "Name", "Term", "Definition");
    assertThat(concept.conceptId()).isEqualTo(123L);
}
```

## Documentation

Documentation is written in AsciiDoc and includes:

- **Architecture diagrams** (Mermaid, PlantUML, GraphViz)
- **Code examples** with syntax highlighting
- **Modular chapters** for maintainability
- **Professional PDF theme** for distribution

### Adding Documentation

1. Create `.adoc` files in `src/docs/asciidoc/`
2. Include in `index.adoc`:

```asciidoc
include::chapters/my-chapter.adoc[]
```

3. Build: `./mvnw verify`

### Including Diagrams

**Mermaid:**
```asciidoc
[mermaid]
....
graph LR
    A[Start] --> B[Process]
    B --> C[End]
....
```

**PlantUML:**
```asciidoc
[plantuml]
....
@startuml
Alice -> Bob: Hello
Bob --> Alice: Hi!
@enduml
....
```

## IntelliJ IDEA Setup

1. **Import Project**: File → Open → Select `pom.xml`
2. **Install Plugin**: AsciiDoc plugin (if not already installed)
3. **AsciiDoc Preview**: Open any `.adoc` file, click preview tab
4. **Run Maven**: Use Maven tool window or run configurations

The `.idea/asciidoc.xml` file pre-configures:
- Attribute resolution for `{generated}` paths
- Source directory location
- Diagram rendering preferences

## Extending the Example

### Add More Java Classes

```bash
# Create new class
touch src/main/java/org/ike/example/Relationship.java

# Create corresponding test
touch src/test/java/org/ike/example/RelationshipTest.java
```

### Add Documentation Dependencies

To include documentation from other projects:

```xml
<dependency>
    <groupId>org.ike.community</groupId>
    <artifactId>ike-core-docs</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <classifier>asciidoc</classifier>
    <type>zip</type>
</dependency>
```

Then include in your `.adoc` files:

```asciidoc
include::{generated}/org.ike.community/ike-core-docs/chapters/terminology.adoc[]
```

## Parent POM Hierarchy

```
ike-parent (AsciiDoc pipeline)
└── ike-java-parent (Java configuration)
    └── example-project (this project)
```

Benefits:
- **Single source of truth** for versions and configuration
- **Consistent builds** across all IKE projects
- **Easy upgrades** by updating parent version

## CI/CD Integration

The project is designed for CI/CD pipelines:

```yaml
# Example GitHub Actions
steps:
  - uses: actions/setup-java@v3
    with:
      java-version: '25'
  - name: Build
    run: ./mvnw clean verify -Dike.pdf.prawn
  - name: Archive Documentation
    uses: actions/upload-artifact@v3
    with:
      name: documentation
      path: target/generated-docs/
```

## License

Apache License 2.0 - See LICENSE file

## Resources

- [IKE Documentation Parent](../ike-parent/README.md)
- [IKE Java Parent](../ike-java-parent/README.md)
- [AsciiDoctor User Manual](https://docs.asciidoctor.org/)
- [SNOMED International](https://www.snomed.org/)

## Support

For issues or questions:
- GitHub Issues: https://github.com/ike-community/ike-maven-asciidoc
- Documentation: See `target/generated-docs/` after building
