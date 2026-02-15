# IKE Java Parent POM

Parent POM for IKE Community Java projects. Extends `ike-parent` to provide comprehensive Java build configuration alongside documentation capabilities.

## Features

- **Java 25**: Latest Java version support with preview features
- **JPMS Ready**: Java Platform Module System configuration
- **Testing**: JUnit 5, AssertJ, Mockito pre-configured
- **Documentation**: Full AsciiDoc pipeline inherited from parent
- **Maven 4.1.0**: Latest Maven model with build/consumer POM separation

## Quick Start

### Use as Parent POM

```xml
<parent>
    <groupId>org.ike.community</groupId>
    <artifactId>ike-java-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
```

### Project Structure

```
your-java-project/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── module-info.java (optional)
│   │   └── resources/
│   ├── test/
│   │   ├── java/
│   │   └── resources/
│   └── docs/
│       └── asciidoc/
│           └── index.adoc
├── pom.xml
└── .idea/
    └── asciidoc.xml
```

## Inherited Capabilities

From `ike-parent`:
- AsciiDoc HTML/PDF generation
- Diagram support (Mermaid, PlantUML, GraphViz)
- Documentation dependency management
- Standard Maven directory layout

See [ike-parent README](../ike-parent/README.md) for details.

## Java Configuration

### Compiler Settings
- **Java Version**: 25 (configurable via `${java.version}`)
- **Release**: Java 25 bytecode
- **Parameters**: Enabled for reflection
- **Warnings**: All warnings enabled except processing

### Testing
- **Unit Tests**: Surefire (runs `*Test.java`)
- **Integration Tests**: Failsafe (runs `*IT.java`)
- **Frameworks**: JUnit 5, AssertJ, Mockito

Example test:
```java
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ExampleTest {
    @Test
    void shouldWork() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
```

### JPMS (Java Modules)

For modular projects, enable JPMS profile:

```bash
mvn clean verify -Pjpms
```

Create `src/main/java/module-info.java`:
```java
module org.ike.example {
    requires java.base;
    exports org.ike.example;
}
```

## Build Lifecycle

### Standard Build
```bash
mvn clean verify
```

This runs:
1. `compile` - Java compilation
2. `test` - Unit tests (Surefire)
3. `package` - JAR creation
4. `verify` - Integration tests + Documentation generation

### Release Build
```bash
mvn clean verify -Prelease
```

Generates:
- Main JAR
- Sources JAR
- Javadoc JAR
- HTML documentation
- PDF documentation (if `-Ppdf` also specified)

### Documentation Only
```bash
mvn verify -Phtml
# or
mvn verify -Ppdf
```

## Properties Reference

### Java Properties
| Property | Default | Description |
|----------|---------|-------------|
| `java.version` | `25` | Java source/target version |
| `maven.compiler.release` | `${java.version}` | Java release version |

### Inherited Properties
See [ike-parent](../ike-parent/README.md#properties-reference)

## Dependencies

### Testing (Pre-configured in dependencyManagement)

```xml
<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Combining Java and Documentation

Your project can have both Java code and comprehensive documentation:

```xml
<project>
    <parent>
        <groupId>org.ike.community</groupId>
        <artifactId>ike-java-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>my-library</artifactId>

    <dependencies>
        <!-- Your Java dependencies -->
    </dependencies>
    
    <build>
        <plugins>
            <!-- Documentation unpacking (if needed) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Build outputs:
- `target/my-library-1.0.0.jar` - Compiled Java
- `target/generated-docs/html/` - HTML documentation
- `target/generated-docs/pdf/` - PDF documentation

## IDE Support

### IntelliJ IDEA
1. Import as Maven project
2. Java 25 SDK auto-configured
3. AsciiDoc plugin configuration inherited
4. Run configurations:
   - `Maven: verify` - Full build
   - `Maven: test` - Unit tests only
   - `Maven: verify -Ppdf` - With PDF docs

## Requirements

- **Maven**: 4.0.0+ (4.1.0 model)
- **Java**: JDK 25+
- **Diagram Tools**: See parent README

## License

Apache License 2.0 - See LICENSE file
