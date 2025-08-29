# GitLab Template Parser

A Java Maven library for parsing and modifying GitLab CI/CD templates while preserving formatting, comments, and custom GitLab tags. Built on SnakeYAML 2.x engine for robust YAML processing with round-trip fidelity.

## Overview

This project provides a parser that can read GitLab CI (`.gitlab-ci.yml` files), modify specific elements like image tags in to-be-continous templates gitlab template and write them back while maintaining:

- Original formatting and spacing
- Comments and their positioning
- Custom GitLab-specific tags (`!reference`, `!include`, etc.)
- Complex YAML structures and nested configurations

e.g. before converted gitlab-ci.yaml that has jdk-11 for gitlab-maven-component will be update to jdk-17, similarly sonar-scanner-cli has 11.4 image tag that will be updated to latest.
```yaml
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
    - !reference [.maven_cache, script]
    - |
      if [ -n "$CI_MERGE_REQUEST_IID" ]; then
        sonar-scanner \
          -Dsonar.projectKey=$CI_PROJECT_PATH_SLUG \
          -Dsonar.sources=. \
          -Dsonar.java.binaries=target/classes \
          -Dsonar.pullrequest.key=$CI_MERGE_REQUEST_IID \
          -Dsonar.pullrequest.branch=$CI_MERGE_REQUEST_SOURCE_BRANCH_NAME \
          -Dsonar.pullrequest.base=$CI_MERGE_REQUEST_TARGET_BRANCH_NAME
      else
        sonar-scanner \
          -Dsonar.projectKey=$CI_PROJECT_PATH_SLUG \
          -Dsonar.sources=. \
          -Dsonar.java.binaries=target/classes \
          -Dsonar.branch.name=$CI_COMMIT_REF_NAME
      fi
  coverage: '/Code coverage: \d+\.\d+/'
  artifacts:
    reports:
      coverage_report:
        coverage_format: cobertura
        path: target/site/cobertura/coverage.xml
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
  <<: *cache_config

# Custom Docker build with multi-stage
docker-build:
  extends: .docker-build
  stage: package
  variables:
    DOCKERFILE_PATH: "Dockerfile"
    DOCKER_BUILD_ARGS: |
      --build-arg JAR_FILE=target/*.jar
      --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
      --build-arg VCS_REF=$CI_COMMIT_SHA
  script:
    - !reference [.docker_login, script]
    - |
      docker build \
        --tag $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA \
        --tag $CI_REGISTRY_IMAGE:latest \
        $DOCKER_BUILD_ARGS \
        .
    - docker push $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
    - docker push $CI_REGISTRY_IMAGE:latest
  dependencies:
    - maven-package

# Trivy security scanning
trivy-scan:
  extends: .trivy
  stage: security-scan
  variables:
    TRIVY_NO_PROGRESS: "true"
    TRIVY_CACHE_DIR: ".trivycache/"
  script:
    - !reference [.trivy_filesystem_scan, script]
    - !reference [.trivy_image_scan, script]
  artifacts:
    reports:
      container_scanning: gl-container-scanning-report.json
      dependency_scanning: gl-dependency-scanning-report.json
    expire_in: 1 week
  cache:
    key: trivy-cache
    paths:
      - .trivycache/

# License scanning job
license-scan:
  image: registry.gitlab.com/gitlab-org/security-products/license-management:latest
  stage: security-scan
  script:
    - /run.sh analyze .
  artifacts:
    reports:
      license_scanning: gl-license-scanning-report.json
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH

# SAST (Static Application Security Testing)
sast:
  stage: security-scan
include:
  - template: Security/SAST.gitlab-ci.yml

# Dependency scanning
dependency_scanning:
  stage: security-scan
include:
  - template: Security/Dependency-Scanning.gitlab-ci.yml

# Custom validation job using job templates
validate-pom:
  image: !reference [.maven_image, image]
  stage: validate
  script:
    - !reference [.maven_cache, script]
    - mvn $MAVEN_CLI_OPTS validate
    - mvn $MAVEN_CLI_OPTS dependency:resolve-sources
  <<: *cache_config
  rules:
    - changes:
        - pom.xml
        - "**/*.java"

# Parallel testing jobs using matrix
test-matrix:
  extends: .maven-test
  parallel:
    matrix:
      - MAVEN_PROFILE: [unit-tests, integration-tests, performance-tests]
  script:
    - !reference [.maven_cache, script]
    - mvn $MAVEN_CLI_OPTS test -P$MAVEN_PROFILE
  artifacts:
    reports:
      junit: target/surefire-reports/TEST-*.xml
    paths:
      - target/

# Deployment jobs with environments
deploy-staging:
  extends: .maven-deploy
  stage: deploy
  environment:
    name: staging
    url: https://staging.$CI_PROJECT_NAME.example.com
    deployment_tier: staging
  script:
    - !reference [.kubectl_setup, script]
    - kubectl set image deployment/$CI_PROJECT_NAME app=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
  rules:
    - if: $CI_COMMIT_BRANCH == "develop"
  resource_group: staging

deploy-production:
  extends: .maven-deploy
  stage: deploy
  environment:
    name: production
    url: https://$CI_PROJECT_NAME.example.com
    deployment_tier: production
  script:
    - !reference [.kubectl_setup, script]
    - kubectl set image deployment/$CI_PROJECT_NAME app=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
  rules:
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
      when: manual
  resource_group: production

# Notification job using external template
notify-teams:
  extends: .teams-notification
  stage: notify
  variables:
    TEAMS_WEBHOOK_URL: $TEAMS_WEBHOOK_URL
    MESSAGE: "Deployment completed for $CI_PROJECT_NAME:$CI_COMMIT_TAG"
  rules:
    - if: $CI_COMMIT_TAG
  when: on_success

# Release job with semantic versioning
release:
  image: registry.gitlab.com/gitlab-org/release-cli:latest
  stage: deploy
  script:
    - echo "Creating release for tag $CI_COMMIT_TAG"
  release:
    tag_name: $CI_COMMIT_TAG
    description: |
      Release $CI_COMMIT_TAG created from commit $CI_COMMIT_SHA
      
      ## Changes
      $CI_COMMIT_MESSAGE
      
      ## Artifacts
      - Docker image: `$CI_REGISTRY_IMAGE:$CI_COMMIT_TAG`
      - Maven artifacts deployed to repository
  rules:
    - if: $CI_COMMIT_TAG

# Workflow rules for pipeline control
workflow:
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
    - if: $CI_COMMIT_TAG
    - if: $CI_PIPELINE_SOURCE == "web"
    - if: $CI_PIPELINE_SOURCE == "schedule"
```

e.g. after converted gitlab-ci.yaml.
```yaml
# GitLab CI Template with To-Be-Continuous Components
# Demonstrates various GitLab CI features: includes, references, templates, etc.

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
    - !reference [.maven_cache, script]
    - |
      if [ -n "$CI_MERGE_REQUEST_IID" ]; then
        sonar-scanner \
          -Dsonar.projectKey=$CI_PROJECT_PATH_SLUG \
          -Dsonar.sources=. \
          -Dsonar.java.binaries=target/classes \
          -Dsonar.pullrequest.key=$CI_MERGE_REQUEST_IID \
          -Dsonar.pullrequest.branch=$CI_MERGE_REQUEST_SOURCE_BRANCH_NAME \
          -Dsonar.pullrequest.base=$CI_MERGE_REQUEST_TARGET_BRANCH_NAME
      else
        sonar-scanner \
          -Dsonar.projectKey=$CI_PROJECT_PATH_SLUG \
          -Dsonar.sources=. \
          -Dsonar.java.binaries=target/classes \
          -Dsonar.branch.name=$CI_COMMIT_REF_NAME
      fi
  coverage: '/Code coverage: \d+\.\d+/'
  artifacts:
    reports:
      coverage_report:
        coverage_format: cobertura
        path: target/site/cobertura/coverage.xml
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
  <<: *cache_config

# Custom Docker build with multi-stage
docker-build:
  extends: .docker-build
  stage: package
  variables:
    DOCKERFILE_PATH: "Dockerfile"
    DOCKER_BUILD_ARGS: |
      --build-arg JAR_FILE=target/*.jar
      --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
      --build-arg VCS_REF=$CI_COMMIT_SHA
  script:
    - !reference [.docker_login, script]
    - |
      docker build \
        --tag $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA \
        --tag $CI_REGISTRY_IMAGE:latest \
        $DOCKER_BUILD_ARGS \
        .
    - docker push $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
    - docker push $CI_REGISTRY_IMAGE:latest
  dependencies:
    - maven-package

# Trivy security scanning
trivy-scan:
  extends: .trivy
  stage: security-scan
  variables:
    TRIVY_NO_PROGRESS: "true"
    TRIVY_CACHE_DIR: ".trivycache/"
  script:
    - !reference [.trivy_filesystem_scan, script]
    - !reference [.trivy_image_scan, script]
  artifacts:
    reports:
      container_scanning: gl-container-scanning-report.json
      dependency_scanning: gl-dependency-scanning-report.json
    expire_in: 1 week
  cache:
    key: trivy-cache
    paths:
      - .trivycache/

# License scanning job
license-scan:
  image: registry.gitlab.com/gitlab-org/security-products/license-management:latest
  stage: security-scan
  script:
    - /run.sh analyze .
  artifacts:
    reports:
      license_scanning: gl-license-scanning-report.json
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH

# SAST (Static Application Security Testing)
sast:
  stage: security-scan
include:
  - template: Security/SAST.gitlab-ci.yml

# Dependency scanning
dependency_scanning:
  stage: security-scan
include:
  - template: Security/Dependency-Scanning.gitlab-ci.yml

# Custom validation job using job templates
validate-pom:
  image: !reference [.maven_image, image]
  stage: validate
  script:
    - !reference [.maven_cache, script]
    - mvn $MAVEN_CLI_OPTS validate
    - mvn $MAVEN_CLI_OPTS dependency:resolve-sources
  <<: *cache_config
  rules:
    - changes:
        - pom.xml
        - "**/*.java"

# Parallel testing jobs using matrix
test-matrix:
  extends: .maven-test
  parallel:
    matrix:
      - MAVEN_PROFILE: [unit-tests, integration-tests, performance-tests]
  script:
    - !reference [.maven_cache, script]
    - mvn $MAVEN_CLI_OPTS test -P$MAVEN_PROFILE
  artifacts:
    reports:
      junit: target/surefire-reports/TEST-*.xml
    paths:
      - target/

# Deployment jobs with environments
deploy-staging:
  extends: .maven-deploy
  stage: deploy
  environment:
    name: staging
    url: https://staging.$CI_PROJECT_NAME.example.com
    deployment_tier: staging
  script:
    - !reference [.kubectl_setup, script]
    - kubectl set image deployment/$CI_PROJECT_NAME app=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
  rules:
    - if: $CI_COMMIT_BRANCH == "develop"
  resource_group: staging

deploy-production:
  extends: .maven-deploy
  stage: deploy
  environment:
    name: production
    url: https://$CI_PROJECT_NAME.example.com
    deployment_tier: production
  script:
    - !reference [.kubectl_setup, script]
    - kubectl set image deployment/$CI_PROJECT_NAME app=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
  rules:
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
      when: manual
  resource_group: production

# Notification job using external template
notify-teams:
  extends: .teams-notification
  stage: notify
  variables:
    TEAMS_WEBHOOK_URL: $TEAMS_WEBHOOK_URL
    MESSAGE: "Deployment completed for $CI_PROJECT_NAME:$CI_COMMIT_TAG"
  rules:
    - if: $CI_COMMIT_TAG
  when: on_success

# Release job with semantic versioning
release:
  image: registry.gitlab.com/gitlab-org/release-cli:latest
  stage: deploy
  script:
    - echo "Creating release for tag $CI_COMMIT_TAG"
  release:
    tag_name: $CI_COMMIT_TAG
    description: |
      Release $CI_COMMIT_TAG created from commit $CI_COMMIT_SHA
      
      ## Changes
      $CI_COMMIT_MESSAGE
      
      ## Artifacts
      - Docker image: `$CI_REGISTRY_IMAGE:$CI_COMMIT_TAG`
      - Maven artifacts deployed to repository
  rules:
    - if: $CI_COMMIT_TAG

# Workflow rules for pipeline control
workflow:
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
    - if: $CI_COMMIT_TAG
    - if: $CI_PIPELINE_SOURCE == "web"
    - if: $CI_PIPELINE_SOURCE == "schedule"

```


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
