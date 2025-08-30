package com.gitlab.template;

import com.gitlab.template.exception.GitLabParseException;
import com.gitlab.template.model.GitLabTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GitLab Tags and References Tests")
class GitLabTagsTest {

    private GitLabTemplateParser parser;

    @BeforeEach
    void setUp() {
        parser = new GitLabTemplateParser();
    }

    @Test
    @DisplayName("Should parse template with YAML anchors and merge keys")
    void shouldParseTemplateWithAnchorsAndMergeKeys() throws GitLabParseException {
        String yamlContent = """
                .cache_template: &cache_config
                  cache:
                    key: ${CI_COMMIT_REF_SLUG}
                    paths:
                      - .m2/repository/
                    policy: pull-push
                
                .maven_image: &maven_image
                  image: maven:3.8-openjdk-11
                
                validate-pom:
                  stage: validate
                  script:
                    - echo "Running validation"
                    - mvn validate
                  <<: [*cache_config, *maven_image]
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);

        assertThat(template).isNotNull();
        assertThat(template.hasJob("validate-pom")).isTrue();
        
        // The anchors and merge keys should be resolved in the parsed structure
        Map<String, Object> validateJob = template.getJob("validate-pom");
        assertThat(validateJob).containsKey("script");
        assertThat(validateJob).containsKey("stage");
        
        // Check if merge key exists and contains the merged properties
        assertThat(validateJob).containsKey("<<");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mergedProps = (List<Map<String, Object>>) validateJob.get("<<");
        assertThat(mergedProps).hasSize(2);
        
        // Verify the merged properties are accessible in the list
        boolean hasCacheConfig = mergedProps.stream().anyMatch(map -> map.containsKey("cache"));
        boolean hasImageConfig = mergedProps.stream().anyMatch(map -> map.containsKey("image"));
        
        assertThat(hasCacheConfig).isTrue();
        assertThat(hasImageConfig).isTrue();
    }

    @Test
    @DisplayName("Should handle YAML anchors and aliases")
    void shouldHandleYAMLAnchorsAndAliases() throws GitLabParseException {
        String yamlContent = """
                .before_script_template: &before_script_common
                  - echo "Starting job on $(date)"
                  - echo "Running on GitLab Runner"
                
                .security_job_template: &security_job
                  stage: security-scan
                  before_script:
                    - *before_script_common
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                
                gitleaks-scan:
                  extends: .gitleaks
                  <<: *security_job
                  variables:
                    GITLEAKS_CONFIG: ".gitleaks.toml"
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);

        assertThat(template).isNotNull();
        assertThat(template.hasJob("gitleaks-scan")).isTrue();
        
        Map<String, Object> gitleaksJob = template.getJob("gitleaks-scan");
        
        // After merge, properties should be directly accessible
        assertThat(gitleaksJob).containsKey("extends");
        assertThat(gitleaksJob).containsKey("variables");
        
        // Check if merge key exists (it contains the merged properties)
        if (gitleaksJob.containsKey("<<")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mergedProps = (Map<String, Object>) gitleaksJob.get("<<");
            assertThat(mergedProps).containsKey("stage");
            assertThat(mergedProps).containsKey("before_script");
            assertThat(mergedProps).containsKey("rules");
        } else {
            // Properties should be merged directly into the job
            assertThat(gitleaksJob).containsKey("stage");
            assertThat(gitleaksJob).containsKey("before_script");
            assertThat(gitleaksJob).containsKey("rules");
        }
    }

    @Test
    @DisplayName("Should parse complex include structures")
    void shouldParseComplexIncludeStructures() throws GitLabParseException {
        String yamlContent = """
                include:
                  # To-Be-Continuous components
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "docker.io/library/maven:3.8-openjdk-11"
                      deploy-enabled: true
                  
                  # Security components
                  - component: $CI_SERVER_FQDN/to-be-continuous/gitleaks/gitlab-ci-gitleaks@2.0
                    inputs:
                      stage: security-scan
                  
                  # Remote includes
                  - remote: 'https://gitlab.com/my-group/ci-templates/-/raw/main/security.yml'
                  
                  # Local includes
                  - local: 'ci-templates/notification-template.yml'
                  
                  # Template includes
                  - template: Security/SAST.gitlab-ci.yml
                
                stages:
                  - validate
                  - security-scan
                  - build
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);

        assertThat(template).isNotNull();
        
        List<Object> includes = template.getInclude();
        assertThat(includes).hasSize(5);
        
        // Check for TBC components
        assertThat(template.hasTBCComponents()).isTrue();
        
        List<Map<String, Object>> mavenComponents = template.getComponentsByPath("maven");
        assertThat(mavenComponents).hasSize(1);
        
        List<Map<String, Object>> gitleaksComponents = template.getComponentsByPath("gitleaks");
        assertThat(gitleaksComponents).hasSize(1);
        
        // Verify different include types
        boolean hasRemote = includes.stream()
                .filter(item -> item instanceof Map)
                .map(item -> (Map<String, Object>) item)
                .anyMatch(item -> item.containsKey("remote"));
        assertThat(hasRemote).isTrue();
        
        boolean hasLocal = includes.stream()
                .filter(item -> item instanceof Map)
                .map(item -> (Map<String, Object>) item)
                .anyMatch(item -> item.containsKey("local"));
        assertThat(hasLocal).isTrue();
        
        boolean hasTemplate = includes.stream()
                .filter(item -> item instanceof Map)
                .map(item -> (Map<String, Object>) item)
                .anyMatch(item -> item.containsKey("template"));
        assertThat(hasTemplate).isTrue();
    }

    @Test
    @DisplayName("Should handle matrix jobs and parallel execution")
    void shouldHandleMatrixJobsAndParallelExecution() throws GitLabParseException {
        String yamlContent = """
                test-matrix:
                  extends: .maven-test
                  parallel:
                    matrix:
                      - MAVEN_PROFILE: [unit-tests, integration-tests, performance-tests]
                        JAVA_VERSION: [11, 17]
                  script:
                    - mvn test -P$MAVEN_PROFILE
                  artifacts:
                    reports:
                      junit: target/surefire-reports/TEST-*.xml
                
                multi-arch-build:
                  stage: build
                  parallel:
                    matrix:
                      - ARCH: [amd64, arm64]
                        OS: [linux, windows]
                  script:
                    - docker build --platform ${OS}/${ARCH} -t myapp:${ARCH}-${OS} .
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);

        assertThat(template).isNotNull();
        assertThat(template.hasJob("test-matrix")).isTrue();
        assertThat(template.hasJob("multi-arch-build")).isTrue();
        
        Map<String, Object> matrixJob = template.getJob("test-matrix");
        assertThat(matrixJob).containsKey("parallel");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> parallel = (Map<String, Object>) matrixJob.get("parallel");
        assertThat(parallel).containsKey("matrix");
    }

    @Test
    @DisplayName("Should handle workflow rules and conditions")
    void shouldHandleWorkflowRulesAndConditions() throws GitLabParseException {
        String yamlContent = """
                workflow:
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
                    - if: $CI_COMMIT_TAG
                    - if: $CI_PIPELINE_SOURCE == "web"
                      when: manual
                
                variables:
                  DEPLOY_ENVIRONMENT:
                    value: "staging"
                    description: "Target deployment environment"
                
                deploy-conditional:
                  stage: deploy
                  script:
                    - echo "Deploying to $DEPLOY_ENVIRONMENT"
                  rules:
                    - if: $CI_COMMIT_BRANCH == "develop"
                      variables:
                        DEPLOY_ENVIRONMENT: "staging"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
                      variables:
                        DEPLOY_ENVIRONMENT: "production"
                      when: manual
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);

        assertThat(template).isNotNull();
        assertThat(template.getData()).containsKey("workflow");
        
        Map<String, Object> variables = template.getVariables();
        assertThat(variables).containsKey("DEPLOY_ENVIRONMENT");
        
        assertThat(template.hasJob("deploy-conditional")).isTrue();
        Map<String, Object> deployJob = template.getJob("deploy-conditional");
        assertThat(deployJob).containsKey("rules");
    }

    @Test
    @DisplayName("Should handle services and complex job configurations")
    void shouldHandleServicesAndComplexJobConfigurations() throws GitLabParseException {
        String yamlContent = """
                services:
                  - name: postgres:13
                    alias: db
                  - name: redis:6-alpine
                    alias: cache
                
                integration-test:
                  image: 
                    name: node:18-alpine
                    entrypoint: [""]
                  services:
                    - name: postgres:13
                      variables:
                        POSTGRES_DB: testdb
                        POSTGRES_USER: testuser
                        POSTGRES_PASSWORD: testpass
                  variables:
                    DATABASE_URL: "postgresql://testuser:testpass@db:5432/testdb"
                    REDIS_URL: "redis://cache:6379"
                  before_script:
                    - apk add --no-cache postgresql-client
                    - until pg_isready -h db -p 5432 -U testuser; do sleep 1; done
                  script:
                    - npm run test:integration
                  artifacts:
                    reports:
                      junit: test-results.xml
                      coverage_report:
                        coverage_format: cobertura
                        path: coverage.xml
                  coverage: '/Coverage: \\d+\\.\\d+%/'
                  retry:
                    max: 2
                    when:
                      - runner_system_failure
                      - stuck_or_timeout_failure
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);

        assertThat(template).isNotNull();
        assertThat(template.getData()).containsKey("services");
        assertThat(template.hasJob("integration-test")).isTrue();
        
        Map<String, Object> integrationJob = template.getJob("integration-test");
        assertThat(integrationJob).containsKey("services");
        assertThat(integrationJob).containsKey("artifacts");
        assertThat(integrationJob).containsKey("retry");
        assertThat(integrationJob).containsKey("coverage");
        
        // Verify complex image structure
        @SuppressWarnings("unchecked")
        Map<String, Object> imageConfig = (Map<String, Object>) integrationJob.get("image");
        assertThat(imageConfig).containsKey("name");
        assertThat(imageConfig).containsKey("entrypoint");
        
        // Verify artifacts structure
        @SuppressWarnings("unchecked")
        Map<String, Object> artifacts = (Map<String, Object>) integrationJob.get("artifacts");
        @SuppressWarnings("unchecked")
        Map<String, Object> reports = (Map<String, Object>) artifacts.get("reports");
        assertThat(reports).containsKey("junit");
        assertThat(reports).containsKey("coverage_report");
    }

    @Test
    @DisplayName("Should handle GitLab !reference tags")
    void shouldHandleGitLabReferenceTags() throws GitLabParseException {
        String yamlContent = """
                .maven_image: &maven_image
                  image: maven:3.8-openjdk-11

                .default_cache: &default_cache
                  cache:
                    key: ${CI_COMMIT_REF_SLUG}
                    paths:
                      - .m2/repository/

                test-job:
                  stage: test
                  image: !reference [.maven_image, image]
                  script:
                    - mvn test
                  <<: *default_cache
                """;

        GitLabTemplate template = parser.parseFromString(yamlContent);

        assertThat(template).isNotNull();
        assertThat(template.hasJob("test-job")).isTrue();
        
        Map<String, Object> testJob = template.getJob("test-job");
        assertThat(testJob).containsKey("image");
        
        // Check that !reference is parsed into structured representation
        Object imageValue = testJob.get("image");
        assertThat(imageValue).isInstanceOf(Map.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> imageRef = (Map<String, Object>) imageValue;
        assertThat(imageRef).containsKey("__gitlab_reference");
        assertThat(imageRef.get("__gitlab_reference")).isEqualTo(true);
        assertThat(imageRef.get("anchor")).isEqualTo(".maven_image");
        assertThat(imageRef.get("property")).isEqualTo("image");
    }
}