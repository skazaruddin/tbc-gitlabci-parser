package com.gitlab.template;

import com.gitlab.template.exception.GitLabDumpException;
import com.gitlab.template.exception.GitLabParseException;
import com.gitlab.template.model.GitLabTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Before/After Transformation Tests as per CLAUDE.md")
class BeforeAfterTransformationTest {

    private GitLabTemplateParser parser;

    @BeforeEach
    void setUp() {
        parser = new GitLabTemplateParser();
    }

    @Test
    @DisplayName("Should transform GitLab CI template from JDK 11 to JDK 17 and sonar 11.4 to latest")
    void shouldTransformGitLabCITemplateAsDescribedInClaudeMdFullYamlAssertionTest() throws GitLabParseException, GitLabDumpException {
        // BEFORE: GitLab CI Template with JDK 11 and sonar-scanner-cli 11.4
        String beforeYaml = """
                # GitLab CI Template with To-Be-Continuous Components

                # Global variables
                variables:
                  # Maven configuration
                  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"
                  MAVEN_CLI_OPTS: "--batch-mode --errors --fail-at-end --show-version"
                  
                  # Docker configuration
                  DOCKER_TLS_CERTDIR: "/certs"
                  DOCKER_DRIVER: overlay2
                  
                  # Sonar configuration
                  SONAR_USER_HOME: "${CI_PROJECT_DIR}/.sonar"
                  GIT_DEPTH: "0"

                # Cache configuration template
                .cache_template: &cache_config
                  cache:
                    key: ${CI_COMMIT_REF_SLUG}
                    paths:
                      - .m2/repository/
                      - .sonar/cache/
                    policy: pull-push

                # Before script template using YAML anchors
                .before_script_template: &before_script_common
                  - echo "Starting job on $(date)"
                  - echo "Running on GitLab Runner $(hostname)"

                # Include external templates and components
                include:
                  # To-Be-Continuous Maven component
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "docker.io/library/maven:3.8-openjdk-11"
                      deploy-enabled: true
                      test-enabled: true
                      
                  # To-Be-Continuous Security components
                  - component: $CI_SERVER_FQDN/to-be-continuous/gitleaks/gitlab-ci-gitleaks@2.0
                    inputs:
                      stage: security-scan
                      
                  - component: $CI_SERVER_FQDN/to-be-continuous/trivy/gitlab-ci-trivy@3.0
                    inputs:
                      image-scanning-enabled: true
                      filesystem-scanning-enabled: true
                      
                  # To-Be-Continuous Docker component
                  - component: $CI_SERVER_FQDN/to-be-continuous/docker/gitlab-ci-docker@4.0
                    inputs:
                      registry: $CI_REGISTRY
                      build-enabled: true
                      
                  # To-Be-Continuous Quality Gate component
                  - component: $CI_SERVER_FQDN/to-be-continuous/sonarqube/gitlab-ci-sonarqube@3.0
                    inputs:
                      stage: quality-gate
                      
                  # Include from remote repository
                  - remote: 'https://gitlab.com/my-group/ci-templates/-/raw/main/security-templates.yml'
                  
                  # Include local template files
                  - local: 'ci-templates/notification-template.yml'
                  - local: 'ci-templates/deployment-helpers.yml'

                # Pipeline stages
                stages:
                  - validate
                  - security-scan
                  - build
                  - test
                  - quality-gate
                  - package
                  - deploy
                  - notify

                # Job templates using YAML anchors and extends
                .security_job_template: &security_job
                  stage: security-scan
                  before_script:
                    - *before_script_common
                  <<: *cache_config
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH

                # Custom Gitleaks job extending the component
                gitleaks-scan:
                  extends: 
                    - .gitleaks
                  <<: *security_job
                  variables:
                    GITLEAKS_CONFIG: ".gitleaks.toml"
                  artifacts:
                    reports:
                      sast: gl-sast-report.json
                    expire_in: 1 week
                    when: always

                # Enhanced SonarQube analysis
                mvn-sonar:
                  image: sonarsource/sonar-scanner-cli:11.4
                  stage: quality-gate
                  variables:
                    SONAR_TOKEN: $SONAR_TOKEN
                    SONAR_HOST_URL: $SONAR_HOST_URL
                  script:
                    - |
                      if [ -n "$CI_MERGE_REQUEST_IID" ]; then
                        sonar-scanner \\
                          -Dsonar.projectKey=$CI_PROJECT_PATH_SLUG \\
                          -Dsonar.sources=. \\
                          -Dsonar.java.binaries=target/classes \\
                          -Dsonar.pullrequest.key=$CI_MERGE_REQUEST_IID \\
                          -Dsonar.pullrequest.branch=$CI_MERGE_REQUEST_SOURCE_BRANCH_NAME \\
                          -Dsonar.pullrequest.base=$CI_MERGE_REQUEST_TARGET_BRANCH_NAME
                      else
                        sonar-scanner \\
                          -Dsonar.projectKey=$CI_PROJECT_PATH_SLUG \\
                          -Dsonar.sources=. \\
                          -Dsonar.java.binaries=target/classes \\
                          -Dsonar.branch.name=$CI_COMMIT_REF_NAME
                      fi
                  coverage: '/Code coverage: \\d+\\.\\d+/'
                  artifacts:
                    reports:
                      coverage_report:
                        coverage_format: cobertura
                        path: target/site/cobertura/coverage.xml
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
                  <<: *cache_config

                # Custom validation job using job templates
                validate-pom:
                  stage: validate
                  script:
                    - mvn validate
                    - mvn dependency:resolve-sources
                  rules:
                    - changes:
                        - pom.xml
                        - "**/*.java"

                # Deployment jobs with environments
                deploy-staging:
                  stage: deploy
                  environment:
                    name: staging
                    url: https://staging.$CI_PROJECT_NAME.example.com
                    deployment_tier: staging
                  script:
                    - kubectl set image deployment/$CI_PROJECT_NAME app=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
                  rules:
                    - if: $CI_COMMIT_BRANCH == "develop"
                  resource_group: staging

                deploy-production:
                  stage: deploy
                  environment:
                    name: production
                    url: https://$CI_PROJECT_NAME.example.com
                    deployment_tier: production
                  script:
                    - kubectl set image deployment/$CI_PROJECT_NAME app=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
                  rules:
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
                      when: manual
                  resource_group: production

                # Workflow rules for pipeline control
                workflow:
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
                    - if: $CI_COMMIT_TAG
                    - if: $CI_PIPELINE_SOURCE == "web"
                    - if: $CI_PIPELINE_SOURCE == "schedule"
                """;

        // Parse the BEFORE template
        GitLabTemplate beforeTemplate = parser.parseFromString(beforeYaml);

        // Verify BEFORE state
        assertThat(beforeTemplate).isNotNull();
        assertThat(beforeTemplate.hasTBCComponents()).isTrue();
        
        // Check BEFORE Maven component with JDK 11
        List<Map<String, Object>> beforeMavenComponents = beforeTemplate.getComponentsByPath("maven");
        assertThat(beforeMavenComponents).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> beforeMavenInputs = (Map<String, Object>) beforeMavenComponents.get(0).get("inputs");
        assertThat(beforeMavenInputs.get("image")).isEqualTo("docker.io/library/maven:3.8-openjdk-11");
        
        // Check BEFORE SonarQube job with version 11.4
        Map<String, Object> beforeSonarJob = beforeTemplate.getJob("mvn-sonar");
        assertThat(beforeSonarJob.get("image")).isEqualTo("sonarsource/sonar-scanner-cli:11.4");

        // TRANSFORMATION: Update Maven image from JDK 11 to JDK 17
        GitLabTemplate step1Template = parser.updateImageInSection(beforeTemplate, "maven", 
                "docker.io/library/maven:3.8-openjdk-17");
        
        // TRANSFORMATION: Update SonarQube image from 11.4 to latest
        GitLabTemplate afterTemplate = parser.updateImageInSection(step1Template, "sonar", 
                "sonarsource/sonar-scanner-cli:latest");

        // EXPECTED AFTER YAML from CLAUDE.md
        String expectedAfterYaml = """
                # GitLab CI Template with To-Be-Continuous Components

                # Global variables
                variables:
                  # Maven configuration
                  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"
                  MAVEN_CLI_OPTS: "--batch-mode --errors --fail-at-end --show-version"
                  
                  # Docker configuration
                  DOCKER_TLS_CERTDIR: "/certs"
                  DOCKER_DRIVER: overlay2
                  
                  # Sonar configuration
                  SONAR_USER_HOME: "${CI_PROJECT_DIR}/.sonar"
                  GIT_DEPTH: "0"

                # Cache configuration template
                .cache_template: &cache_config
                  cache:
                    key: ${CI_COMMIT_REF_SLUG}
                    paths:
                      - .m2/repository/
                      - .sonar/cache/
                    policy: pull-push

                # Before script template using YAML anchors
                .before_script_template: &before_script_common
                  - echo "Starting job on $(date)"
                  - echo "Running on GitLab Runner $(hostname)"

                # Include external templates and components
                include:
                  # To-Be-Continuous Maven component
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "docker.io/library/maven:3.8-openjdk-17"
                      deploy-enabled: true
                      test-enabled: true
                      
                  # To-Be-Continuous Security components
                  - component: $CI_SERVER_FQDN/to-be-continuous/gitleaks/gitlab-ci-gitleaks@2.0
                    inputs:
                      stage: security-scan
                      
                  - component: $CI_SERVER_FQDN/to-be-continuous/trivy/gitlab-ci-trivy@3.0
                    inputs:
                      image-scanning-enabled: true
                      filesystem-scanning-enabled: true
                      
                  # To-Be-Continuous Docker component
                  - component: $CI_SERVER_FQDN/to-be-continuous/docker/gitlab-ci-docker@4.0
                    inputs:
                      registry: $CI_REGISTRY
                      build-enabled: true
                      
                  # To-Be-Continuous Quality Gate component
                  - component: $CI_SERVER_FQDN/to-be-continuous/sonarqube/gitlab-ci-sonarqube@3.0
                    inputs:
                      stage: quality-gate
                      
                  # Include from remote repository
                  - remote: 'https://gitlab.com/my-group/ci-templates/-/raw/main/security-templates.yml'
                  
                  # Include local template files
                  - local: 'ci-templates/notification-template.yml'
                  - local: 'ci-templates/deployment-helpers.yml'

                # Pipeline stages
                stages:
                  - validate
                  - security-scan
                  - build
                  - test
                  - quality-gate
                  - package
                  - deploy
                  - notify

                # Job templates using YAML anchors and extends
                .security_job_template: &security_job
                  stage: security-scan
                  before_script:
                    - *before_script_common
                  <<: *cache_config
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH

                # Custom Gitleaks job extending the component
                gitleaks-scan:
                  extends: 
                    - .gitleaks
                  <<: *security_job
                  variables:
                    GITLEAKS_CONFIG: ".gitleaks.toml"
                  artifacts:
                    reports:
                      sast: gl-sast-report.json
                    expire_in: 1 week
                    when: always

                # Enhanced SonarQube analysis
                mvn-sonar:
                  image: sonarsource/sonar-scanner-cli:latest
                  stage: quality-gate
                  variables:
                    SONAR_TOKEN: $SONAR_TOKEN
                    SONAR_HOST_URL: $SONAR_HOST_URL
                  script:
                    - |
                      if [ -n "$CI_MERGE_REQUEST_IID" ]; then
                        sonar-scanner \\
                          -Dsonar.projectKey=$CI_PROJECT_PATH_SLUG \\
                          -Dsonar.sources=. \\
                          -Dsonar.java.binaries=target/classes \\
                          -Dsonar.pullrequest.key=$CI_MERGE_REQUEST_IID \\
                          -Dsonar.pullrequest.branch=$CI_MERGE_REQUEST_SOURCE_BRANCH_NAME \\
                          -Dsonar.pullrequest.base=$CI_MERGE_REQUEST_TARGET_BRANCH_NAME
                      else
                        sonar-scanner \\
                          -Dsonar.projectKey=$CI_PROJECT_PATH_SLUG \\
                          -Dsonar.sources=. \\
                          -Dsonar.java.binaries=target/classes \\
                          -Dsonar.branch.name=$CI_COMMIT_REF_NAME
                      fi
                  coverage: '/Code coverage: \\d+\\.\\d+/'
                  artifacts:
                    reports:
                      coverage_report:
                        coverage_format: cobertura
                        path: target/site/cobertura/coverage.xml
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
                  <<: *cache_config

                # Custom validation job using job templates
                validate-pom:
                  stage: validate
                  script:
                    - mvn validate
                    - mvn dependency:resolve-sources
                  rules:
                    - changes:
                        - pom.xml
                        - "**/*.java"

                # Deployment jobs with environments
                deploy-staging:
                  stage: deploy
                  environment:
                    name: staging
                    url: https://staging.$CI_PROJECT_NAME.example.com
                    deployment_tier: staging
                  script:
                    - kubectl set image deployment/$CI_PROJECT_NAME app=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
                  rules:
                    - if: $CI_COMMIT_BRANCH == "develop"
                  resource_group: staging

                deploy-production:
                  stage: deploy
                  environment:
                    name: production
                    url: https://$CI_PROJECT_NAME.example.com
                    deployment_tier: production
                  script:
                    - kubectl set image deployment/$CI_PROJECT_NAME app=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
                  rules:
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
                      when: manual
                  resource_group: production

                # Workflow rules for pipeline control
                workflow:
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
                    - if: $CI_COMMIT_TAG
                    - if: $CI_PIPELINE_SOURCE == "web"
                    - if: $CI_PIPELINE_SOURCE == "schedule"
                """;

        // Generate actual after YAML
        String actualAfterYaml = parser.dumpToString(afterTemplate);

        // Parse both expected and actual to normalize for comparison
        GitLabTemplate expectedTemplate = parser.parseFromString(expectedAfterYaml);
        
        // Verify key transformations match expected YAML
        List<Map<String, Object>> actualMavenComponents = afterTemplate.getComponentsByPath("maven");
        List<Map<String, Object>> expectedMavenComponents = expectedTemplate.getComponentsByPath("maven");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> actualMavenInputs = (Map<String, Object>) actualMavenComponents.get(0).get("inputs");
        @SuppressWarnings("unchecked")
        Map<String, Object> expectedMavenInputs = (Map<String, Object>) expectedMavenComponents.get(0).get("inputs");
        
        // Assert Maven image was updated to JDK 17 as expected
        assertThat(actualMavenInputs.get("image")).isEqualTo("docker.io/library/maven:3.8-openjdk-17");
        assertThat(actualMavenInputs.get("image")).isEqualTo(expectedMavenInputs.get("image"));
        
        // Assert SonarQube job image was updated to latest as expected
        Map<String, Object> actualSonarJob = afterTemplate.getJob("mvn-sonar");
        Map<String, Object> expectedSonarJob = expectedTemplate.getJob("mvn-sonar");
        
        assertThat(actualSonarJob.get("image")).isEqualTo("sonarsource/sonar-scanner-cli:latest");
        assertThat(actualSonarJob.get("image")).isEqualTo(expectedSonarJob.get("image"));

        // Verify structure integrity matches expected
        assertThat(afterTemplate.getStages()).isEqualTo(expectedTemplate.getStages());
        assertThat(afterTemplate.getVariables()).isEqualTo(expectedTemplate.getVariables());
        
        // Verify that both templates have the same TBC components and jobs
        assertThat(afterTemplate.hasTBCComponents()).isTrue();
        assertThat(expectedTemplate.hasTBCComponents()).isTrue();
        assertThat(afterTemplate.hasJob("gitleaks-scan")).isEqualTo(expectedTemplate.hasJob("gitleaks-scan"));
        assertThat(afterTemplate.hasJob("mvn-sonar")).isEqualTo(expectedTemplate.hasJob("mvn-sonar"));
        
        // Assert that the transformation achieved the expected result
        assertThat(actualAfterYaml).contains("maven:3.8-openjdk-17");
        assertThat(actualAfterYaml).contains("sonar-scanner-cli:latest");
       
    }

    @Test
    @DisplayName("Should transform GitLab CI template from JDK 11 to JDK 17 and sonar 11.4 to latest")
    void shouldTransformGitLabCITemplateAsDescribedInClaudeMd() throws GitLabParseException, GitLabDumpException {
        // BEFORE: GitLab CI Template with JDK 11 and sonar-scanner-cli 11.4
        String beforeYaml = """
                # GitLab CI Template with To-Be-Continuous Components

                # Global variables
                variables:
                  # Maven configuration
                  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"
                  MAVEN_CLI_OPTS: "--batch-mode --errors --fail-at-end --show-version"
                  
                  # Docker configuration
                  DOCKER_TLS_CERTDIR: "/certs"
                  DOCKER_DRIVER: overlay2
                  
                  # Sonar configuration
                  SONAR_USER_HOME: "${CI_PROJECT_DIR}/.sonar"
                  GIT_DEPTH: "0"

                # Cache configuration template
                .cache_template: &cache_config
                  cache:
                    key: ${CI_COMMIT_REF_SLUG}
                    paths:
                      - .m2/repository/
                      - .sonar/cache/
                    policy: pull-push

                # Before script template using YAML anchors
                .before_script_template: &before_script_common
                  - echo "Starting job on $(date)"
                  - echo "Running on GitLab Runner $(hostname)"

                # Include external templates and components
                include:
                  # To-Be-Continuous Maven component
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "docker.io/library/maven:3.8-openjdk-11"
                      deploy-enabled: true
                      test-enabled: true
                      
                  # To-Be-Continuous Security components
                  - component: $CI_SERVER_FQDN/to-be-continuous/gitleaks/gitlab-ci-gitleaks@2.0
                    inputs:
                      stage: security-scan
                      
                  - component: $CI_SERVER_FQDN/to-be-continuous/trivy/gitlab-ci-trivy@3.0
                    inputs:
                      image-scanning-enabled: true
                      filesystem-scanning-enabled: true
                      
                  # To-Be-Continuous Docker component
                  - component: $CI_SERVER_FQDN/to-be-continuous/docker/gitlab-ci-docker@4.0
                    inputs:
                      registry: $CI_REGISTRY
                      build-enabled: true
                      
                  # To-Be-Continuous Quality Gate component
                  - component: $CI_SERVER_FQDN/to-be-continuous/sonarqube/gitlab-ci-sonarqube@3.0
                    inputs:
                      stage: quality-gate
                      
                  # Include from remote repository
                  - remote: 'https://gitlab.com/my-group/ci-templates/-/raw/main/security-templates.yml'
                  
                  # Include local template files
                  - local: 'ci-templates/notification-template.yml'
                  - local: 'ci-templates/deployment-helpers.yml'

                # Pipeline stages
                stages:
                  - validate
                  - security-scan
                  - build
                  - test
                  - quality-gate
                  - package
                  - deploy
                  - notify

                # Job templates using YAML anchors and extends
                .security_job_template: &security_job
                  stage: security-scan
                  before_script:
                    - *before_script_common
                  <<: *cache_config
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH

                # Custom Gitleaks job extending the component
                gitleaks-scan:
                  extends: 
                    - .gitleaks
                  <<: *security_job
                  variables:
                    GITLEAKS_CONFIG: ".gitleaks.toml"
                  artifacts:
                    reports:
                      sast: gl-sast-report.json
                    expire_in: 1 week
                    when: always

                # Enhanced SonarQube analysis
                mvn-sonar:
                  image: sonarsource/sonar-scanner-cli:11.4
                  stage: quality-gate
                  variables:
                    SONAR_TOKEN: $SONAR_TOKEN
                    SONAR_HOST_URL: $SONAR_HOST_URL
                  script:
                    - |
                      if [ -n "$CI_MERGE_REQUEST_IID" ]; then
                        sonar-scanner \\
                          -Dsonar.projectKey=$CI_PROJECT_PATH_SLUG \\
                          -Dsonar.sources=. \\
                          -Dsonar.java.binaries=target/classes \\
                          -Dsonar.pullrequest.key=$CI_MERGE_REQUEST_IID \\
                          -Dsonar.pullrequest.branch=$CI_MERGE_REQUEST_SOURCE_BRANCH_NAME \\
                          -Dsonar.pullrequest.base=$CI_MERGE_REQUEST_TARGET_BRANCH_NAME
                      else
                        sonar-scanner \\
                          -Dsonar.projectKey=$CI_PROJECT_PATH_SLUG \\
                          -Dsonar.sources=. \\
                          -Dsonar.java.binaries=target/classes \\
                          -Dsonar.branch.name=$CI_COMMIT_REF_NAME
                      fi
                  coverage: '/Code coverage: \\d+\\.\\d+/'
                  artifacts:
                    reports:
                      coverage_report:
                        coverage_format: cobertura
                        path: target/site/cobertura/coverage.xml
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
                  <<: *cache_config

                # Custom validation job using job templates
                validate-pom:
                  stage: validate
                  script:
                    - mvn validate
                    - mvn dependency:resolve-sources
                  rules:
                    - changes:
                        - pom.xml
                        - "**/*.java"

                # Deployment jobs with environments
                deploy-staging:
                  stage: deploy
                  environment:
                    name: staging
                    url: https://staging.$CI_PROJECT_NAME.example.com
                    deployment_tier: staging
                  script:
                    - kubectl set image deployment/$CI_PROJECT_NAME app=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
                  rules:
                    - if: $CI_COMMIT_BRANCH == "develop"
                  resource_group: staging

                deploy-production:
                  stage: deploy
                  environment:
                    name: production
                    url: https://$CI_PROJECT_NAME.example.com
                    deployment_tier: production
                  script:
                    - kubectl set image deployment/$CI_PROJECT_NAME app=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
                  rules:
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
                      when: manual
                  resource_group: production

                # Workflow rules for pipeline control
                workflow:
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
                    - if: $CI_COMMIT_TAG
                    - if: $CI_PIPELINE_SOURCE == "web"
                    - if: $CI_PIPELINE_SOURCE == "schedule"
                """;

        // Parse the BEFORE template
        GitLabTemplate beforeTemplate = parser.parseFromString(beforeYaml);

        // Verify BEFORE state
        assertThat(beforeTemplate).isNotNull();
        assertThat(beforeTemplate.hasTBCComponents()).isTrue();
        
        // Check BEFORE Maven component with JDK 11
        List<Map<String, Object>> beforeMavenComponents = beforeTemplate.getComponentsByPath("maven");
        assertThat(beforeMavenComponents).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> beforeMavenInputs = (Map<String, Object>) beforeMavenComponents.get(0).get("inputs");
        assertThat(beforeMavenInputs.get("image")).isEqualTo("docker.io/library/maven:3.8-openjdk-11");
        
        // Check BEFORE SonarQube job with version 11.4
        Map<String, Object> beforeSonarJob = beforeTemplate.getJob("mvn-sonar");
        assertThat(beforeSonarJob.get("image")).isEqualTo("sonarsource/sonar-scanner-cli:11.4");

        // TRANSFORMATION: Update Maven image from JDK 11 to JDK 17
        GitLabTemplate step1Template = parser.updateImageInSection(beforeTemplate, "maven", 
                "docker.io/library/maven:3.8-openjdk-17");
        
        // TRANSFORMATION: Update SonarQube image from 11.4 to latest
        GitLabTemplate afterTemplate = parser.updateImageInSection(step1Template, "sonar", 
                "sonarsource/sonar-scanner-cli:latest");

        // Verify AFTER transformations
        assertThat(afterTemplate).isNotNull();
        
        // Check AFTER Maven component with JDK 17
        List<Map<String, Object>> afterMavenComponents = afterTemplate.getComponentsByPath("maven");
        assertThat(afterMavenComponents).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> afterMavenInputs = (Map<String, Object>) afterMavenComponents.get(0).get("inputs");
        assertThat(afterMavenInputs.get("image")).isEqualTo("docker.io/library/maven:3.8-openjdk-17");
        
        // Check AFTER SonarQube job with latest version
        Map<String, Object> afterSonarJob = afterTemplate.getJob("mvn-sonar");
        assertThat(afterSonarJob.get("image")).isEqualTo("sonarsource/sonar-scanner-cli:latest");

        // Verify all image references are updated correctly
        Map<String, String> allAfterImages = afterTemplate.getAllImageReferences();
        assertThat(allAfterImages).containsValue("docker.io/library/maven:3.8-openjdk-17");
        assertThat(allAfterImages).containsValue("sonarsource/sonar-scanner-cli:latest");
        
        // Verify structure preservation
        assertThat(afterTemplate.getStages()).hasSize(8);
        assertThat(afterTemplate.getInclude()).hasSize(8); // 5 components + 1 remote + 2 local
        assertThat(afterTemplate.getVariables()).isNotEmpty();
        assertThat(afterTemplate.getData()).containsKey("workflow");
        
        // Verify YAML anchors and merge keys are preserved
        assertThat(afterTemplate.hasJob("gitleaks-scan")).isTrue();
        assertThat(afterTemplate.getData()).containsKey(".security_job_template");
        
        // Verify TBC components are still detected after transformation
        assertThat(afterTemplate.hasTBCComponents()).isTrue();
        assertThat(afterTemplate.getComponentsByPath("gitleaks")).hasSize(1);
        assertThat(afterTemplate.getComponentsByPath("trivy")).hasSize(1);
        assertThat(afterTemplate.getComponentsByPath("docker")).hasSize(1);
        assertThat(afterTemplate.getComponentsByPath("sonarqube")).hasSize(1);

        // Test serialization of the transformed template
        String afterYaml = parser.dumpToString(afterTemplate);
        assertThat(afterYaml).isNotNull();
        assertThat(afterYaml).contains("maven:3.8-openjdk-17");
        assertThat(afterYaml).contains("sonar-scanner-cli:latest");
        
        // Verify round-trip parsing maintains transformations
        GitLabTemplate roundtripTemplate = parser.parseFromString(afterYaml);
        
        List<Map<String, Object>> roundtripMavenComponents = roundtripTemplate.getComponentsByPath("maven");
        @SuppressWarnings("unchecked")
        Map<String, Object> roundtripMavenInputs = (Map<String, Object>) roundtripMavenComponents.get(0).get("inputs");
        assertThat(roundtripMavenInputs.get("image")).isEqualTo("docker.io/library/maven:3.8-openjdk-17");
        
        Map<String, Object> roundtripSonarJob = roundtripTemplate.getJob("mvn-sonar");
        assertThat(roundtripSonarJob.get("image")).isEqualTo("sonarsource/sonar-scanner-cli:latest");
    }

    @Test
    @DisplayName("Should preserve complex GitLab CI structures during transformation")
    void shouldPreserveComplexStructuresDuringTransformation() throws GitLabParseException, GitLabDumpException {
        String complexYaml = """
                # Complex GitLab CI with anchors, references, and multiple components

                variables:
                  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"
                  DOCKER_TLS_CERTDIR: "/certs"

                .cache_template: &cache_config
                  cache:
                    key: ${CI_COMMIT_REF_SLUG}
                    paths:
                      - .m2/repository/
                    policy: pull-push

                .security_job_template: &security_job
                  stage: security-scan
                  <<: *cache_config
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH

                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "docker.io/library/maven:3.8-openjdk-11"
                      deploy-enabled: true
                  - component: $CI_SERVER_FQDN/to-be-continuous/docker/gitlab-ci-docker@4.0
                    inputs:
                      registry: $CI_REGISTRY
                  - template: Security/SAST.gitlab-ci.yml
                  - remote: 'https://gitlab.com/templates/security.yml'

                stages:
                  - validate
                  - build
                  - test
                  - security
                  - deploy

                # Job with complex image configuration
                integration-test:
                  image:
                    name: maven:3.8-openjdk-11
                    entrypoint: [""]
                  services:
                    - name: postgres:13
                      variables:
                        POSTGRES_DB: testdb
                  stage: test
                  script:
                    - mvn test -Pintegration
                  artifacts:
                    reports:
                      junit: target/surefire-reports/TEST-*.xml
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH

                # Custom sonar job
                code-quality:
                  image: sonarsource/sonar-scanner-cli:11.4
                  script:
                    - sonar-scanner -Dsonar.projectKey=$CI_PROJECT_NAME
                  only:
                    - merge_requests
                    - main
                  <<: *security_job

                # Deployment with matrix
                deploy:
                  stage: deploy
                  parallel:
                    matrix:
                      - ENVIRONMENT: [staging, production]
                  script:
                    - echo "Deploying to $ENVIRONMENT"
                  rules:
                    - if: $CI_COMMIT_BRANCH == "main"
                      variables:
                        ENVIRONMENT: production
                    - if: $CI_COMMIT_BRANCH == "develop"
                      variables:
                        ENVIRONMENT: staging

                workflow:
                  rules:
                    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
                    - if: $CI_COMMIT_BRANCH =~ /^(main|develop)$/
                """;

        GitLabTemplate originalTemplate = parser.parseFromString(complexYaml);
        
        // Perform transformations
        GitLabTemplate step1 = parser.updateImageInSection(originalTemplate, "maven", 
                "docker.io/library/maven:3.8-openjdk-17");
        GitLabTemplate transformedTemplate = parser.updateImageInSection(step1, "sonar", 
                "sonarsource/sonar-scanner-cli:latest");

        // Verify transformations
        List<Map<String, Object>> mavenComponents = transformedTemplate.getComponentsByPath("maven");
        @SuppressWarnings("unchecked")
        Map<String, Object> mavenInputs = (Map<String, Object>) mavenComponents.get(0).get("inputs");
        assertThat(mavenInputs.get("image")).isEqualTo("docker.io/library/maven:3.8-openjdk-17");

        // Verify complex image structure transformation
        Map<String, Object> integrationJob = transformedTemplate.getJob("integration-test");
        @SuppressWarnings("unchecked")
        Map<String, Object> imageConfig = (Map<String, Object>) integrationJob.get("image");
        assertThat(imageConfig.get("name")).isEqualTo("docker.io/library/maven:3.8-openjdk-17");
        assertThat(imageConfig.get("entrypoint")).isNotNull();

        // Verify sonar job transformation
        Map<String, Object> codeQualityJob = transformedTemplate.getJob("code-quality");
        assertThat(codeQualityJob.get("image")).isEqualTo("sonarsource/sonar-scanner-cli:latest");

        // Verify structure preservation
        assertThat(transformedTemplate.getStages()).containsExactly("validate", "build", "test", "security", "deploy");
        assertThat(transformedTemplate.getInclude()).hasSize(4);
        assertThat(transformedTemplate.getData()).containsKey("workflow");

        // Verify complex job configurations are preserved
        assertThat(integrationJob).containsKey("services");
        assertThat(integrationJob).containsKey("artifacts");
        assertThat(integrationJob).containsKey("rules");

        Map<String, Object> deployJob = transformedTemplate.getJob("deploy");
        assertThat(deployJob).containsKey("parallel");
        @SuppressWarnings("unchecked")
        Map<String, Object> parallel = (Map<String, Object>) deployJob.get("parallel");
        assertThat(parallel).containsKey("matrix");

        // Test serialization maintains structure
        String serialized = parser.dumpToString(transformedTemplate);
        assertThat(serialized).contains("3.8-openjdk-17");
        assertThat(serialized).contains("sonar-scanner-cli:latest");
        assertThat(serialized).contains("postgres:13");
        assertThat(serialized).contains("matrix");
    }

    @Test
    @DisplayName("Should handle multiple image updates in single template")
    void shouldHandleMultipleImageUpdatesInSingleTemplate() throws GitLabParseException {
        String multiImageYaml = """
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "maven:3.8-openjdk-11"
                  - component: $CI_SERVER_FQDN/to-be-continuous/docker/gitlab-ci-docker@4.0
                    inputs:
                      image: "docker:20.10.16"

                stages:
                  - build
                  - test
                  - quality

                maven-build:
                  image: maven:3.8-openjdk-11
                  stage: build
                  script:
                    - mvn clean compile

                docker-build:
                  image: docker:20.10.16
                  stage: build
                  script:
                    - docker build -t myapp .

                sonar-analysis:
                  image: sonarsource/sonar-scanner-cli:11.4
                  stage: quality
                  script:
                    - sonar-scanner

                unit-tests:
                  image:
                    name: maven:3.8-openjdk-11
                    entrypoint: [""]
                  stage: test
                  script:
                    - mvn test
                """;

        GitLabTemplate originalTemplate = parser.parseFromString(multiImageYaml);

        // Apply multiple transformations
        GitLabTemplate step1 = parser.updateImageInSection(originalTemplate, "maven", "maven:3.8-openjdk-17");
        GitLabTemplate step2 = parser.updateImageInSection(step1, "docker", "docker:24.0.7");
        GitLabTemplate finalTemplate = parser.updateImageInSection(step2, "sonar", "sonarsource/sonar-scanner-cli:latest");

        // Verify Maven component update
        List<Map<String, Object>> mavenComponents = finalTemplate.getComponentsByPath("maven");
        @SuppressWarnings("unchecked")
        Map<String, Object> mavenInputs = (Map<String, Object>) mavenComponents.get(0).get("inputs");
        assertThat(mavenInputs.get("image")).isEqualTo("maven:3.8-openjdk-17");

        // Verify Docker component update
        List<Map<String, Object>> dockerComponents = finalTemplate.getComponentsByPath("docker");
        @SuppressWarnings("unchecked")
        Map<String, Object> dockerInputs = (Map<String, Object>) dockerComponents.get(0).get("inputs");
        assertThat(dockerInputs.get("image")).isEqualTo("docker:24.0.7");

        // Verify job image updates
        assertThat(finalTemplate.getJob("maven-build").get("image")).isEqualTo("maven:3.8-openjdk-17");
        assertThat(finalTemplate.getJob("docker-build").get("image")).isEqualTo("docker:24.0.7");
        assertThat(finalTemplate.getJob("sonar-analysis").get("image")).isEqualTo("sonarsource/sonar-scanner-cli:latest");

        // Verify complex image structure update
        Map<String, Object> unitTestsJob = finalTemplate.getJob("unit-tests");
        @SuppressWarnings("unchecked")
        Map<String, Object> imageConfig = (Map<String, Object>) unitTestsJob.get("image");
        assertThat(imageConfig.get("name")).isEqualTo("maven:3.8-openjdk-17");

        // Verify comprehensive image reference tracking
        Map<String, String> allImages = finalTemplate.getAllImageReferences();
        assertThat(allImages.values()).contains(
                "maven:3.8-openjdk-17",
                "docker:24.0.7", 
                "sonarsource/sonar-scanner-cli:latest"
        );
    }
}