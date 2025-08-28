# GitLab Template Parser

A Java Maven library for parsing and modifying GitLab CI/CD templates while preserving formatting, comments, and custom GitLab tags. Built on SnakeYAML 2.x engine for robust YAML processing with round-trip fidelity.

## Overview

This project provides a parser that can read GitLab CI templates (`.gitlab-ci.yml` files), modify specific elements like image tags in components, and write them back while maintaining:

- Original formatting and spacing
- Comments and their positioning
- Custom GitLab-specific tags (`!reference`, `!include`, etc.)
- Complex YAML structures and nested configurations

## Features

- **Round-trip parsing**: Parse and dump YAML with complete fidelity
- **Image tag replacement**: Update Docker image tags in job definitions
- **GitLab tag preservation**: Maintains `!reference`, `!include`, and other GitLab-specific YAML tags
- **Comment preservation**: Keeps all comments in their original positions
- **Whitespace retention**: Preserves original indentation and spacing
- **Component modification**: Update reusable components while maintaining structure
- **TBC (To Be Continuous) template support**: Special handling for TBC template patterns

## Requirements

- Java 8 or higher
- Maven 3.6+
- SnakeYAML 2.x

## Installation

### Maven Dependency

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.gitlab.template</groupId>
    <artifactId>gitlab-template-parser</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Build from Source

```bash
git clone <repository-url>
cd gitlab-template-parser
mvn clean install
```

## Quick Start

```java
import com.gitlab.template.GitLabTemplateParser;
import com.gitlab.template.model.GitLabTemplate;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Example {
    public static void main(String[] args) throws Exception {
        // Initialize parser
        GitLabTemplateParser parser = new GitLabTemplateParser();
        
        // Load and parse a GitLab CI template
        String content = Files.readString(Paths.get(".gitlab-ci.yml"));
        GitLabTemplate template = parser.parse(content);
        
        // Update image tags
        parser.updateImageTag(template, "my-service", "old-tag:1.0", "new-tag:2.0");
        
        // Write back with preserved formatting
        String output = parser.dump(template);
        Files.writeString(Paths.get(".gitlab-ci.yml"), output);
    }
}
```

## API Reference

### GitLabTemplateParser

Main parser class for handling GitLab CI templates.

#### Constructor

```java
// Default constructor with standard options
GitLabTemplateParser parser = new GitLabTemplateParser();

// Constructor with custom options
GitLabTemplateParser parser = new GitLabTemplateParser(
    GitLabParserOptions.builder()
        .preserveQuotes(true)
        .preserveOrder(true)
        .indent(2)
        .width(120)
        .build()
);
```

#### Methods

##### `parse(String content) -> GitLabTemplate`

Parse a GitLab CI YAML string into a structured template object.

**Parameters:**
- `content`: Raw YAML content as string

**Returns:**
- `GitLabTemplate`: Parsed template object with preserved metadata

**Throws:**
- `GitLabParseException`: If YAML parsing fails

##### `dump(GitLabTemplate template) -> String`

Convert a GitLabTemplate object back to YAML string.

**Parameters:**
- `template`: GitLabTemplate object to serialize

**Returns:**
- `String`: YAML string with preserved formatting

**Throws:**
- `GitLabDumpException`: If serialization fails

##### `updateImageTag(GitLabTemplate template, String component, String oldTag, String newTag) -> boolean`

Update Docker image tags in specified components.

**Parameters:**
- `template`: GitLabTemplate object to modify
- `component`: Name of the component/job to update
- `oldTag`: Current image tag to replace
- `newTag`: New image tag to use

**Returns:**
- `boolean`: True if replacement was successful

##### `findComponents(GitLabTemplate template) -> List<String>`

List all components/jobs in the template.

**Parameters:**
- `template`: GitLabTemplate object to analyze

**Returns:**
- `List<String>`: Names of all components found

##### `hasImage(GitLabTemplate template, String component) -> boolean`

Check if a component has an image definition.

##### `getImage(GitLabTemplate template, String component) -> Optional<String>`

Get the current image tag for a component.

## Usage Examples

### Basic Image Tag Replacement

```java
import com.gitlab.template.GitLabTemplateParser;
import com.gitlab.template.model.GitLabTemplate;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BasicExample {
    public void updateImageTag() throws Exception {
        GitLabTemplateParser parser = new GitLabTemplateParser();
        
        // Load template
        String content = Files.readString(Paths.get(".gitlab-ci.yml"));
        GitLabTemplate template = parser.parse(content);
        
        // Update a specific service image
        boolean success = parser.updateImageTag(
            template,
            "build-service",
            "node:16-alpine",
            "node:18-alpine"
        );
        
        if (success) {
            // Save changes
            String output = parser.dump(template);
            Files.writeString(Paths.get(".gitlab-ci.yml"), output);
            System.out.println("Image tag updated successfully");
        }
    }
}
```

### Working with TBC Templates

```java
public class TbcExample {
    public void handleTbcTemplate() throws Exception {
        String tbcContent = """
            include:
              - project: 'to-be-continuous/tools'
                ref: '3.0.0'
                file: '/templates/.gitlab-ci.yml'
            
            variables:
              TBC_VERSION: "3.0.0"
            
            my-job:
              extends: .tbc-base
              image: !reference [.docker, image]
              script:
                - echo "Building application"
            """;
        
        GitLabTemplateParser parser = new GitLabTemplateParser();
        GitLabTemplate template = parser.parse(tbcContent);
        
        // The parser preserves TBC-specific structures
        String output = parser.dump(template);
        // Output maintains !reference tags and TBC formatting
        
        assert output.contains("!reference [.docker, image]");
    }
}
```

### Preserving Comments and Custom Tags

```java
public class CommentPreservationExample {
    public void preserveComments() throws Exception {
        String yamlWithComments = """
            # Main GitLab CI configuration
            include:
              - !include 'common/.gitlab-ci.yml'  # Common definitions
            
            variables:
              # Application version
              APP_VERSION: "1.0.0"
              
            build:
              # Build job configuration
              stage: build
              image: 
                name: docker:20.10.16
                # Use specific Docker version
              extends: !reference [.base-job, configuration]
              script:
                - docker build -t app:$APP_VERSION .
            """;
        
        GitLabTemplateParser parser = new GitLabTemplateParser();
        GitLabTemplate template = parser.parse(yamlWithComments);
        
        // All comments and !reference/!include tags are preserved
        String output = parser.dump(template);
        
        assert output.contains("# Main GitLab CI configuration");
        assert output.contains("!reference [.base-job, configuration]");
        assert output.contains("!include 'common/.gitlab-ci.yml'");
    }
}
```

### Bulk Component Updates

```java
import java.util.List;
import java.util.Optional;

public class BulkUpdateExample {
    
    public void updateAllNodeImages(String templatePath, String newNodeVersion) throws Exception {
        GitLabTemplateParser parser = new GitLabTemplateParser();
        
        String content = Files.readString(Paths.get(templatePath));
        GitLabTemplate template = parser.parse(content);
        
        // Find all components
        List<String> components = parser.findComponents(template);
        
        // Update all Node.js images
        for (String component : components) {
            if (parser.hasImage(template, component)) {
                Optional<String> currentImage = parser.getImage(template, component);
                if (currentImage.isPresent() && currentImage.get().startsWith("node:")) {
                    String newImage = "node:" + newNodeVersion;
                    parser.updateImageTag(template, component, currentImage.get(), newImage);
                }
            }
        }
        
        // Save updated template
        String output = parser.dump(template);
        Files.writeString(Paths.get(templatePath), output);
    }
}
```

### Stream Processing for Large Files

```java
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class StreamExample {
    
    public void processLargeTemplate() throws Exception {
        GitLabTemplateParser parser = new GitLabTemplateParser();
        
        try (InputStream inputStream = Files.newInputStream(Paths.get("large-template.yml"))) {
            GitLabTemplate template = parser.parse(inputStream);
            
            // Process template
            parser.updateImageTag(template, "heavy-job", "ubuntu:20.04", "ubuntu:22.04");
            
            try (OutputStream outputStream = Files.newOutputStream(Paths.get("updated-template.yml"))) {
                parser.dump(template, outputStream);
            }
        }
    }
}
```

## GitLab-Specific Features

### Supported GitLab Tags

The parser fully supports and preserves:

- `!reference`: Reference other parts of the configuration
- `!include`: Include external YAML files
- Custom tags used in GitLab CI/CD pipelines

### TBC Template Support

Special handling for To Be Continuous (TBC) templates:

- Preserves TBC-specific directory structures
- Maintains TBC variable conventions
- Supports TBC component inheritance patterns

## Configuration

### GitLabParserOptions

```java
GitLabParserOptions options = GitLabParserOptions.builder()
    .preserveQuotes(true)        // Keep original quote styles
    .preserveOrder(true)         // Maintain key order
    .explicitStart(false)        // Don't add --- document start
    .explicitEnd(false)          // Don't add ... document end
    .width(120)                  // Line width for formatting
    .indent(2)                   // Indentation spaces
    .allowDuplicateKeys(false)   // Strict YAML parsing
    .build();

GitLabTemplateParser parser = new GitLabTemplateParser(options);
```

### Custom Tag Resolvers

```java
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;

public class CustomGitLabConstructor extends Constructor {
    public CustomGitLabConstructor() {
        super();
        // Add custom GitLab tag constructors
        this.yamlConstructors.put(new Tag("!reference"), new ConstructReference());
        this.yamlConstructors.put(new Tag("!include"), new ConstructInclude());
    }
}
```

## Testing

### Maven Test Commands

```bash
# Run all tests
mvn test

# Run specific test classes
mvn test -Dtest=GitLabParserTest
mvn test -Dtest=RoundTripTest
mvn test -Dtest=ImageUpdateTest

# Run tests with coverage
mvn jacoco:prepare-agent test jacoco:report

# Integration tests
mvn integration-test

# Performance tests
mvn test -Dtest=PerformanceTest
```

### Test Structure

```
src/test/java/
├── com/gitlab/template/
│   ├── GitLabParserTest.java           # Basic parsing tests
│   ├── RoundTripTest.java              # Round-trip fidelity tests
│   ├── GitLabTagsTest.java             # GitLab-specific tag tests
│   ├── ImageUpdateTest.java            # Image replacement tests
│   ├── TbcTemplateTest.java            # TBC template tests
│   ├── CommentPreservationTest.java    # Comment preservation tests
│   ├── EdgeCaseTest.java               # Edge cases and error handling
│   └── PerformanceTest.java            # Performance benchmarks
```

### Example Test Cases

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

public class GitLabTagsTest {
    
    @Test
    @DisplayName("Should preserve !reference tags exactly")
    public void testPreserveGitLabReferences() throws Exception {
        String yamlContent = """
            job:
              extends: !reference [.base, config]
              script: !reference [.scripts, build]
            """;
        
        GitLabTemplateParser parser = new GitLabTemplateParser();
        GitLabTemplate template = parser.parse(yamlContent);
        String output = parser.dump(template);
        
        assertTrue(output.contains("!reference [.base, config]"));
        assertTrue(output.contains("!reference [.scripts, build]"));
    }
    
    @Test
    @DisplayName("Should survive round-trip processing with comments")
    public void testRoundTripWithComments() throws Exception {
        String yamlWithComments = """
            # Job definition
            build:
              stage: build  # Build stage
              script:
                - make build  # Run build
            """;
        
        GitLabTemplateParser parser = new GitLabTemplateParser();
        GitLabTemplate template = parser.parse(yamlWithComments);
        String output = parser.dump(template);
        
        assertTrue(output.contains("# Job definition"));
        assertTrue(output.contains("# Build stage"));
        assertTrue(output.contains("# Run build"));
    }
    
    @Test
    @DisplayName("Should handle complex nested structures")
    public void testComplexNestedStructures() throws Exception {
        String complexYaml = """
            variables:
              DOCKER_REGISTRY: !reference [.vars, registry]
              
            .base-job: &base
              image: docker:latest
              before_script: !reference [.scripts, setup]
              
            build-app:
              <<: *base
              extends: !reference [.jobs, build-base]
              variables:
                APP_NAME: !reference [.vars, app-name]
              script:
                - !reference [.scripts, build]
                - !reference [.scripts, test]
            """;
        
        GitLabTemplateParser parser = new GitLabTemplateParser();
        GitLabTemplate template = parser.parse(complexYaml);
        
        // Test that we can update image tags in complex structures
        boolean updated = parser.updateImageTag(template, "build-app", "docker:latest", "docker:20.10.16");
        assertTrue(updated);
        
        String output = parser.dump(template);
        assertTrue(output.contains("docker:20.10.16"));
        // Ensure all references are preserved
        assertTrue(output.contains("!reference [.vars, registry]"));
        assertTrue(output.contains("!reference [.scripts, setup]"));
    }
}
```

### Integration Tests

```java
@Test
@DisplayName("Integration test with real GitLab CI file")
public void testRealGitLabCiFile() throws Exception {
    // Load a real .gitlab-ci.yml file from test resources
    String realTemplate = Files.readString(
        Paths.get("src/test/resources/real-gitlab-ci.yml")
    );
    
    GitLabTemplateParser parser = new GitLabTemplateParser();
    GitLabTemplate template = parser.parse(realTemplate);
    
    // Perform some modifications
    List<String> components = parser.findComponents(template);
    assertFalse(components.isEmpty());
    
    // Update multiple image tags
    parser.updateImageTag(template, "build", "maven:3.6-jdk-11", "maven:3.8-jdk-17");
    parser.updateImageTag(template, "test", "openjdk:11", "openjdk:17");
    
    // Ensure round-trip preserves structure
    String output = parser.dump(template);
    GitLabTemplate reparsed = parser.parse(output);
    
    assertEquals(parser.findComponents(template), parser.findComponents(reparsed));
}
```

## Maven Configuration

### pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.gitlab.template</groupId>
    <artifactId>gitlab-template-parser</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    
    <name>GitLab Template Parser</name>
    <description>Parse and modify GitLab CI templates with round-trip fidelity</description>
    
    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <snakeyaml.version>2.2</snakeyaml.version>
        <junit.version>5.10.0</junit.version>
    </properties>
    
    <dependencies>
        <!-- SnakeYAML 2.x for YAML processing -->
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
            <version>${snakeyaml.version}</version>
        </dependency>
        
        <!-- Apache Commons for utilities -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>3.13.0</version>
        </dependency>
        
        <!-- SLF4J for logging -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.9</version>
        </dependency>
        
        <!-- Test dependencies -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
        
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>5.5.0</version>
            <scope>test</scope>
        </dependency>
        
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>3.24.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <!-- Compiler plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>8</source>
                    <target>8</target>
                </configuration>
            </plugin>
            
            <!-- Surefire plugin for tests -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.1.2</version>
            </plugin>
            
            <!-- Failsafe plugin for integration tests -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>3.1.2</version>
            </plugin>
            
            <!-- JaCoCo for code coverage -->
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.10</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>prepare-agent</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>test</phase>
                        <goals>
                            <goal>report</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            
            <!-- Javadoc plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-javadoc-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <source>8</source>
                </configuration>
            </plugin>
            
            <!-- Source plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-source-plugin</artifactId>
                <version>3.3.0</version>
                <executions>
                    <execution>
                        <id>attach-sources</id>
                        <goals>
                            <goal>jar</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
    
    <profiles>
        <!-- Performance testing profile -->
        <profile>
            <id>performance</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-plugin</artifactId>
                        <configuration>
                            <includes>
                                <include>**/*PerformanceTest.java</include>
                            </includes>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

## Error Handling

### Exception Types

```java
// Custom exceptions for better error handling
public class GitLabParseException extends Exception {
    public GitLabParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class GitLabDumpException extends Exception {
    public GitLabDumpException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class InvalidComponentException extends RuntimeException {
    public InvalidComponentException(String component) {
        super("Component not found: " + component);
    }
}
```

### Error Handling Example

```java
public class ErrorHandlingExample {
    
    public void safeTemplateProcessing(String templatePath) {
        GitLabTemplateParser parser = new GitLabTemplateParser();
        
        try {
            String content = Files.readString(Paths.get(templatePath));
            GitLabTemplate template = parser.parse(content);
            
            parser.updateImageTag(template, "build-job", "old:tag", "new:tag");
            
            String output = parser.dump(template);
            Files.writeString(Paths.get(templatePath), output);
            
        } catch (GitLabParseException e) {
            System.err.println("Failed to parse GitLab template: " + e.getMessage());
            e.printStackTrace();
        } catch (GitLabDumpException e) {
            System.err.println("Failed to serialize GitLab template: " + e.getMessage());
            e.printStackTrace();
        } catch (InvalidComponentException e) {
            System.err.println("Component error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

## Performance Considerations

### Memory Usage

```java
// For large templates, use streaming when possible
public void processLargeTemplate() throws Exception {
    GitLabTemplateParser parser = new GitLabTemplateParser(
        GitLabParserOptions.builder()
            .useStreaming(true)
            .bufferSize(8192)
            .build()
    );
    
    // Process in chunks to manage memory
}
```

### Benchmarking

```java
@Test
public void benchmarkParsingPerformance() {
    GitLabTemplateParser parser = new GitLabTemplateParser();
    String largeTemplate = generateLargeTemplate(1000); // 1000 jobs
    
    long startTime = System.nanoTime();
    
    for (int i = 0; i < 100; i++) {
        GitLabTemplate template = parser.parse(largeTemplate);
        parser.dump(template);
    }
    
    long endTime = System.nanoTime();
    long avgTime = (endTime - startTime) / 100;
    
    System.out.println("Average processing time: " + avgTime + " ns");
    assertTrue(avgTime < 10_000_000); // Less than 10ms
}
```

## Contributing

### Development Setup

```bash
# Clone repository
git clone <repository-url>
cd gitlab-template-parser

# Build project
mvn clean compile

# Run tests
mvn test

# Generate documentation
mvn javadoc:javadoc

# Create distribution
mvn clean package
```

### Code Style

The project follows standard Java conventions:

- Use meaningful variable and method names
- Add Javadoc comments for public APIs
- Follow Maven standard directory layout
- Include comprehensive tests for new features

### Pull Request Process

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/new-feature`
3. Make changes and add tests
4. Ensure all tests pass: `mvn test`
5. Update documentation if needed
6. Submit a pull request

## License

MIT License. See LICENSE file for details.

## Changelog

### v1.0.0
- Initial release
- Basic parsing and round-trip functionality using SnakeYAML 2.x
- GitLab tag preservation
- Image tag replacement
- TBC template support
- Comprehensive test suite

## Support

For issues, questions, or contributions:

- Create an issue on the GitHub repository
- Check existing documentation and examples
- Review test cases for usage patterns

## Roadmap

- [ ] Support for GitLab CI/CD variables expansion
- [ ] Integration with GitLab API for template fetching
- [ ] CLI tool for batch processing
- [ ] YAML validation against GitLab CI schema
- [ ] Support for GitLab CI/CD includes resolution
- [ ] Spring Boot integration
- [ ] Gradle plugin support
