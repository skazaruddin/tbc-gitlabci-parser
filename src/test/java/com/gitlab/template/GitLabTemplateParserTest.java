package com.gitlab.template;

import com.gitlab.template.exception.GitLabDumpException;
import com.gitlab.template.exception.GitLabParseException;
import com.gitlab.template.model.GitLabParserOptions;
import com.gitlab.template.model.GitLabTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GitLab Template Parser Tests")
class GitLabTemplateParserTest {

    private GitLabTemplateParser parser;

    @BeforeEach
    void setUp() {
        parser = new GitLabTemplateParser();
    }

    @Test
    @DisplayName("Should parse simple GitLab CI template from string")
    void shouldParseSimpleTemplateFromString() throws GitLabParseException {
        String yamlContent = """
                variables:
                  DOCKER_REGISTRY: "registry.example.com"
                  APP_VERSION: "1.0.0"
                
                stages:
                  - build
                  - test
                  - deploy
                
                build-app:
                  stage: build
                  image: docker:20.10.16
                  script:
                    - echo "Building application"
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);

        assertThat(template).isNotNull();
        assertThat(template.getData()).containsKey("variables");
        assertThat(template.getData()).containsKey("stages");
        assertThat(template.getData()).containsKey("build-app");
        
        Map<String, Object> variables = template.getVariables();
        assertThat(variables).containsEntry("DOCKER_REGISTRY", "registry.example.com");
        assertThat(variables).containsEntry("APP_VERSION", "1.0.0");
        
        List<String> stages = template.getStages();
        assertThat(stages).containsExactly("build", "test", "deploy");
        
        assertThat(template.hasJob("build-app")).isTrue();
    }

    @Test
    @DisplayName("Should parse template with TBC components")
    void shouldParseTemplateWithTBCComponents() throws GitLabParseException {
        String yamlContent = """
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "docker.io/library/maven:3.8-openjdk-11"
                      deploy-enabled: true
                      test-enabled: true
                
                variables:
                  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"
                
                stages:
                  - build
                  - test
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);

        assertThat(template).isNotNull();
        assertThat(template.hasTBCComponents()).isTrue();
        
        List<Object> includes = template.getInclude();
        assertThat(includes).hasSize(1);
        
        List<Map<String, Object>> mavenComponents = template.getComponentsByPath("maven");
        assertThat(mavenComponents).hasSize(1);
        
        Map<String, String> imageRefs = template.getAllImageReferences();
        assertThat(imageRefs).containsValue("docker.io/library/maven:3.8-openjdk-11");
    }

    @Test
    @DisplayName("Should parse template from InputStream")
    void shouldParseTemplateFromInputStream() throws GitLabParseException {
        String yamlContent = """
                variables:
                  TEST_VAR: "test-value"
                
                stages:
                  - test
                """;

        try (InputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes(StandardCharsets.UTF_8))) {
            GitLabTemplate template = parser.parseFromInputStream(inputStream);
            
            assertThat(template).isNotNull();
            assertThat(template.getVariables()).containsEntry("TEST_VAR", "test-value");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should parse template from Reader")
    void shouldParseTemplateFromReader() throws GitLabParseException {
        String yamlContent = """
                variables:
                  TEST_VAR: "reader-test"
                
                stages:
                  - build
                """;

        try (StringReader reader = new StringReader(yamlContent)) {
            GitLabTemplate template = parser.parseFromReader(reader);
            
            assertThat(template).isNotNull();
            assertThat(template.getVariables()).containsEntry("TEST_VAR", "reader-test");
        }
    }

    @Test
    @DisplayName("Should serialize template to YAML string")
    void shouldSerializeTemplateToString() throws GitLabParseException, GitLabDumpException {
        String yamlContent = """
                variables:
                  TEST_VAR: "test-value"
                
                stages:
                  - build
                  - test
                
                test-job:
                  stage: test
                  script:
                    - echo "Testing"
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);
        String serialized = parser.dumpToString(template);

        assertThat(serialized).isNotNull();
        assertThat(serialized).contains("TEST_VAR");
        assertThat(serialized).contains("test-value");
        assertThat(serialized).contains("build");
        assertThat(serialized).contains("test");
    }

    @Test
    @DisplayName("Should serialize template to Writer")
    void shouldSerializeTemplateToWriter() throws GitLabParseException, GitLabDumpException, IOException {
        String yamlContent = """
                variables:
                  WRITER_TEST: "writer-value"
                
                stages:
                  - deploy
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);
        
        try (StringWriter writer = new StringWriter()) {
            parser.dumpToWriter(template, writer);
            String result = writer.toString();
            
            assertThat(result).contains("WRITER_TEST");
            assertThat(result).contains("writer-value");
            assertThat(result).contains("deploy");
        }
    }

    @Test
    @DisplayName("Should update image in component inputs")
    void shouldUpdateImageInComponentInputs() throws GitLabParseException {
        String yamlContent = """
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "docker.io/library/maven:3.8-openjdk-11"
                      deploy-enabled: true
                
                variables:
                  TEST_VAR: "test"
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);
        GitLabTemplate updated = parser.updateImageInSection(template, "maven", 
                "docker.io/library/maven:3.8-openjdk-17");

        List<Map<String, Object>> mavenComponents = updated.getComponentsByPath("maven");
        assertThat(mavenComponents).hasSize(1);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) mavenComponents.get(0).get("inputs");
        assertThat(inputs.get("image")).isEqualTo("docker.io/library/maven:3.8-openjdk-17");
    }

    @Test
    @DisplayName("Should update image in job definitions")
    void shouldUpdateImageInJobDefinitions() throws GitLabParseException {
        String yamlContent = """
                variables:
                  TEST_VAR: "test"
                
                mvn-sonar:
                  image: sonarsource/sonar-scanner-cli:11.4
                  stage: quality-gate
                  script:
                    - sonar-scanner
                
                test-with-complex-image:
                  image:
                    name: node:18-alpine
                    entrypoint: [""]
                  script:
                    - npm test
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);
        GitLabTemplate updated = parser.updateImageInSection(template, "sonar", 
                "sonarsource/sonar-scanner-cli:latest");

        Map<String, Object> sonarJob = updated.getJob("mvn-sonar");
        assertThat(sonarJob.get("image")).isEqualTo("sonarsource/sonar-scanner-cli:latest");
        
        // Complex image should remain unchanged since it's a different image
        @SuppressWarnings("unchecked")
        Map<String, Object> complexImageJob = (Map<String, Object>) updated.getJob("test-with-complex-image");
        @SuppressWarnings("unchecked")
        Map<String, Object> complexImage = (Map<String, Object>) complexImageJob.get("image");
        assertThat(complexImage.get("name")).isEqualTo("node:18-alpine");
    }

    @Test
    @DisplayName("Should throw exception for null or empty YAML content")
    void shouldThrowExceptionForNullOrEmptyContent() {
        assertThatThrownBy(() -> parser.parseFromString(null))
                .isInstanceOf(GitLabParseException.class)
                .hasMessageContaining("YAML content cannot be null or empty");

        assertThatThrownBy(() -> parser.parseFromString(""))
                .isInstanceOf(GitLabParseException.class)
                .hasMessageContaining("YAML content cannot be null or empty");

        assertThatThrownBy(() -> parser.parseFromString("   "))
                .isInstanceOf(GitLabParseException.class)
                .hasMessageContaining("YAML content cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for invalid YAML")
    void shouldThrowExceptionForInvalidYAML() {
        String invalidYaml = """
                variables:
                  TEST_VAR: "unclosed quote
                
                stages:
                  - build
                """;

        assertThatThrownBy(() -> parser.parseFromString(invalidYaml))
                .isInstanceOf(GitLabParseException.class)
                .hasMessageContaining("Failed to parse YAML content");
    }

    @Test
    @DisplayName("Should throw exception for non-map root element")
    void shouldThrowExceptionForNonMapRoot() {
        String listYaml = """
                - item1
                - item2
                - item3
                """;

        assertThatThrownBy(() -> parser.parseFromString(listYaml))
                .isInstanceOf(GitLabParseException.class)
                .hasMessageContaining("Root YAML element must be a map/object");
    }

    @Test
    @DisplayName("Should handle custom parser options")
    void shouldHandleCustomParserOptions() {
        GitLabParserOptions customOptions = GitLabParserOptions.builder()
                .preserveQuotes(false)
                .indent(4)
                .width(80)
                .build();

        GitLabTemplateParser customParser = new GitLabTemplateParser(customOptions);
        
        assertThat(customParser.getOptions().getIndent()).isEqualTo(4);
        assertThat(customParser.getOptions().getWidth()).isEqualTo(80);
        assertThat(customParser.getOptions().isPreserveQuotes()).isFalse();
    }
}