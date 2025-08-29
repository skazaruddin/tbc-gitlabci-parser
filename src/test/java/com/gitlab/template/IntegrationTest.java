package com.gitlab.template;

import com.gitlab.template.exception.GitLabDumpException;
import com.gitlab.template.exception.GitLabParseException;
import com.gitlab.template.model.GitLabParserOptions;
import com.gitlab.template.model.GitLabTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Integration Tests")
class IntegrationTest {

    private GitLabTemplateParser parser;

    @BeforeEach
    void setUp() {
        parser = new GitLabTemplateParser();
    }

    @Test
    @DisplayName("Should parse and roundtrip sample GitLab CI file")
    void shouldParseAndRoundtripSampleFile() throws GitLabParseException, GitLabDumpException, IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/sample-gitlab-ci.yml")) {
            assertThat(inputStream).isNotNull();
            
            GitLabTemplate template = parser.parseFromInputStream(inputStream);
            
            assertThat(template).isNotNull();
            assertThat(template.getData()).isNotEmpty();
            
            // Verify basic structure
            assertThat(template.getData()).containsKey("variables");
            assertThat(template.getData()).containsKey("stages");
            
            // Verify variables
            Map<String, Object> variables = template.getVariables();
            assertThat(variables).containsEntry("DOCKER_REGISTRY", "registry.example.com");
            assertThat(variables).containsEntry("APP_VERSION", "1.0.0");
            
            // Verify stages
            List<String> stages = template.getStages();
            assertThat(stages).contains("build", "test", "deploy");
            
            // Verify jobs
            assertThat(template.hasJob("build-app")).isTrue();
            assertThat(template.hasJob("test-unit")).isTrue();
            assertThat(template.hasJob("deploy-staging")).isTrue();
            
            // Test roundtrip
            String serialized = parser.dumpToString(template);
            assertThat(serialized).isNotNull();
            assertThat(serialized).isNotEmpty();
            
            // Parse the serialized content back
            GitLabTemplate roundtripTemplate = parser.parseFromString(serialized);
            assertThat(roundtripTemplate.getData()).isEqualTo(template.getData());
        }
    }

    @Test
    @DisplayName("Should handle complex TBC template transformation")
    void shouldHandleComplexTBCTemplateTransformation() throws GitLabParseException {
        String complexYaml = "variables:\n" +
                "  MAVEN_OPTS: \"-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository\"\n" +
                "  DOCKER_TLS_CERTDIR: \"/certs\"\n" +
                "  SONAR_USER_HOME: \"${CI_PROJECT_DIR}/.sonar\"\n" +
                "\n" +
                "include:\n" +
                "  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0\n" +
                "    inputs:\n" +
                "      image: \"docker.io/library/maven:3.8-openjdk-11\"\n" +
                "      deploy-enabled: true\n" +
                "      test-enabled: true\n" +
                "  - component: $CI_SERVER_FQDN/to-be-continuous/sonarqube/gitlab-ci-sonarqube@3.0\n" +
                "    inputs:\n" +
                "      stage: quality-gate\n" +
                "\n" +
                "stages:\n" +
                "  - validate\n" +
                "  - build\n" +
                "  - test\n" +
                "  - quality-gate\n" +
                "  - deploy\n" +
                "\n" +
                "mvn-sonar:\n" +
                "  image: sonarsource/sonar-scanner-cli:11.4\n" +
                "  stage: quality-gate\n" +
                "  script:\n" +
                "    - sonar-scanner\n";

        GitLabTemplate template = parser.parseFromString(complexYaml);

        // Verify TBC components are detected
        assertThat(template.hasTBCComponents()).isTrue();
        
        List<Map<String, Object>> mavenComponents = template.getComponentsByPath("maven");
        assertThat(mavenComponents).hasSize(1);
        
        List<Map<String, Object>> sonarComponents = template.getComponentsByPath("sonarqube");
        assertThat(sonarComponents).hasSize(1);
        
        // Test image updates
        GitLabTemplate updatedTemplate = parser.updateImageInSection(template, "maven", 
                "docker.io/library/maven:3.8-openjdk-17");
        
        List<Map<String, Object>> updatedMavenComponents = updatedTemplate.getComponentsByPath("maven");
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) updatedMavenComponents.get(0).get("inputs");
        assertThat(inputs.get("image")).isEqualTo("docker.io/library/maven:3.8-openjdk-17");
        
        // Verify that sonar job image was also updated if applicable
        GitLabTemplate sonarUpdated = parser.updateImageInSection(updatedTemplate, "sonar", 
                "sonarsource/sonar-scanner-cli:latest");
        
        Map<String, Object> sonarJob = sonarUpdated.getJob("mvn-sonar");
        assertThat(sonarJob.get("image")).isEqualTo("sonarsource/sonar-scanner-cli:latest");
        
        // Verify all image references
        Map<String, String> imageRefs = sonarUpdated.getAllImageReferences();
        assertThat(imageRefs).containsValue("docker.io/library/maven:3.8-openjdk-17");
        assertThat(imageRefs).containsValue("sonarsource/sonar-scanner-cli:latest");
    }

    @Test
    @DisplayName("Should preserve formatting with custom options")
    void shouldPreserveFormattingWithCustomOptions() throws GitLabParseException, GitLabDumpException {
        GitLabParserOptions customOptions = GitLabParserOptions.builder()
                .preserveQuotes(true)
                .indent(4)
                .width(100)
                .preserveOrder(true)
                .build();

        GitLabTemplateParser customParser = new GitLabTemplateParser(customOptions);

        String yamlContent = "variables:\n" +
                "  TEST_VAR: \"quoted-value\"\n" +
                "  NUMERIC_VAR: 42\n" +
                "\n" +
                "stages:\n" +
                "  - build\n" +
                "  - test\n" +
                "\n" +
                "test-job:\n" +
                "  stage: test\n" +
                "  image: \"alpine:latest\"\n" +
                "  script:\n" +
                "    - echo \"Running tests\"\n";

        GitLabTemplate template = customParser.parseFromString(yamlContent);
        String serialized = customParser.dumpToString(template);

        assertThat(serialized).isNotNull();
        assertThat(serialized).contains("TEST_VAR");
        assertThat(serialized).contains("build");
        assertThat(serialized).contains("test");
        
        // Verify parser options are applied
        assertThat(customParser.getOptions().getIndent()).isEqualTo(4);
        assertThat(customParser.getOptions().getWidth()).isEqualTo(100);
        assertThat(customParser.getOptions().isPreserveQuotes()).isTrue();
        assertThat(customParser.getOptions().isPreserveOrder()).isTrue();
    }

    @Test
    @DisplayName("Should handle real-world GitLab CI template patterns")
    void shouldHandleRealWorldGitLabCITemplatePatterns() throws GitLabParseException {
        String realWorldYaml = "# GitLab CI Template with To-Be-Continuous Components\n" +
                "\n" +
                "variables:\n" +
                "  MAVEN_OPTS: \"-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository\"\n" +
                "  MAVEN_CLI_OPTS: \"--batch-mode --errors --fail-at-end --show-version\"\n" +
                "  DOCKER_TLS_CERTDIR: \"/certs\"\n" +
                "  DOCKER_DRIVER: overlay2\n" +
                "  SONAR_USER_HOME: \"${CI_PROJECT_DIR}/.sonar\"\n" +
                "  GIT_DEPTH: \"0\"\n" +
                "\n" +
                "include:\n" +
                "  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0\n" +
                "    inputs:\n" +
                "      image: \"docker.io/library/maven:3.8-openjdk-11\"\n" +
                "      deploy-enabled: true\n" +
                "      test-enabled: true\n" +
                "  - component: $CI_SERVER_FQDN/to-be-continuous/gitleaks/gitlab-ci-gitleaks@2.0\n" +
                "    inputs:\n" +
                "      stage: security-scan\n" +
                "  - component: $CI_SERVER_FQDN/to-be-continuous/trivy/gitlab-ci-trivy@3.0\n" +
                "    inputs:\n" +
                "      image-scanning-enabled: true\n" +
                "      filesystem-scanning-enabled: true\n" +
                "  - remote: 'https://gitlab.com/my-group/ci-templates/-/raw/main/security-templates.yml'\n" +
                "  - local: 'ci-templates/notification-template.yml'\n" +
                "\n" +
                "stages:\n" +
                "  - validate\n" +
                "  - security-scan\n" +
                "  - build\n" +
                "  - test\n" +
                "  - quality-gate\n" +
                "  - package\n" +
                "  - deploy\n" +
                "  - notify\n" +
                "\n" +
                "mvn-sonar:\n" +
                "  image: sonarsource/sonar-scanner-cli:11.4\n" +
                "  stage: quality-gate\n" +
                "  variables:\n" +
                "    SONAR_TOKEN: $SONAR_TOKEN\n" +
                "    SONAR_HOST_URL: $SONAR_HOST_URL\n" +
                "  script:\n" +
                "    - sonar-scanner\n" +
                "\n" +
                "workflow:\n" +
                "  rules:\n" +
                "    - if: $CI_PIPELINE_SOURCE == \"merge_request_event\"\n" +
                "    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH\n" +
                "    - if: $CI_COMMIT_TAG\n";

        GitLabTemplate template = parser.parseFromString(realWorldYaml);

        // Verify comprehensive structure parsing
        assertThat(template).isNotNull();
        assertThat(template.hasTBCComponents()).isTrue();
        
        // Verify multiple TBC components
        assertThat(template.getComponentsByPath("maven")).hasSize(1);
        assertThat(template.getComponentsByPath("gitleaks")).hasSize(1);
        assertThat(template.getComponentsByPath("trivy")).hasSize(1);
        
        // Verify include diversity
        List<Object> includes = template.getInclude();
        assertThat(includes).hasSize(5); // 3 components + 1 remote + 1 local
        
        // Verify workflow configuration
        assertThat(template.getData()).containsKey("workflow");
        
        // Test comprehensive image updates
        GitLabTemplate updated1 = parser.updateImageInSection(template, "maven", 
                "docker.io/library/maven:3.8-openjdk-17");
        GitLabTemplate updated2 = parser.updateImageInSection(updated1, "sonar", 
                "sonarsource/sonar-scanner-cli:latest");
        
        // Verify updates were applied
        List<Map<String, Object>> updatedMavenComponents = updated2.getComponentsByPath("maven");
        @SuppressWarnings("unchecked")
        Map<String, Object> mavenInputs = (Map<String, Object>) updatedMavenComponents.get(0).get("inputs");
        assertThat(mavenInputs.get("image")).isEqualTo("docker.io/library/maven:3.8-openjdk-17");
        
        Map<String, Object> sonarJob = updated2.getJob("mvn-sonar");
        assertThat(sonarJob.get("image")).isEqualTo("sonarsource/sonar-scanner-cli:latest");
        
        // Verify comprehensive image tracking
        Map<String, String> allImages = updated2.getAllImageReferences();
        assertThat(allImages).isNotEmpty();
        assertThat(allImages.values()).contains(
                "docker.io/library/maven:3.8-openjdk-17",
                "sonarsource/sonar-scanner-cli:latest"
        );
    }
}