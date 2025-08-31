package com.gitlab.template.openrewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.yaml.Assertions;

import static org.openrewrite.yaml.Assertions.yaml;

/**
 * Tests for the UpdateGitLabImageTags OpenRewrite recipe.
 * Demonstrates how the recipe integrates with the GitLab Template Parser.
 */
class UpdateGitLabImageTagsTest implements RewriteTest {
    
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpdateGitLabImageTags()
                .withComponentPath("maven")
                .withNewImage("maven:3.8-openjdk-17"));
    }
    
    @Test
    void shouldUpdateMavenImageInTBCComponent() {
        rewriteRun(
            yaml(
                """
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "docker.io/library/maven:3.8-openjdk-11"
                      deploy-enabled: true
                
                stages:
                  - validate
                  - build
                """,
                """
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "maven:3.8-openjdk-17"
                      deploy-enabled: true
                
                stages:
                  - validate
                  - build
                """,
                spec -> spec.path(".gitlab-ci.yml")
            )
        );
    }
    
    @Test
    void shouldUpdateMavenImageInJobs() {
        rewriteRun(
            yaml(
                """
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "maven:3.8-openjdk-11"
                
                test-job:
                  stage: test
                  image: maven:3.8-openjdk-11
                  script:
                    - mvn test
                
                build-job:
                  stage: build
                  image: 
                    name: maven:3.8-openjdk-11
                  script:
                    - mvn compile
                """,
                """
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "maven:3.8-openjdk-17"
                
                test-job:
                  stage: test
                  image: maven:3.8-openjdk-17
                  script:
                    - mvn test
                
                build-job:
                  stage: build
                  image: 
                    name: maven:3.8-openjdk-17
                  script:
                    - mvn compile
                """,
                spec -> spec.path(".gitlab-ci.yml")
            )
        );
    }
    
    @Test
    void shouldHandleComplexTemplateWithAnchors() {
        rewriteRun(
            yaml(
                """
                .maven_image: &maven_image
                  image: maven:3.8-openjdk-11
                
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "maven:3.8-openjdk-11"
                
                test-job:
                  stage: test
                  <<: *maven_image
                  script:
                    - mvn test
                """,
                """
                .maven_image: &maven_image
                  image: maven:3.8-openjdk-11
                
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "maven:3.8-openjdk-17"
                
                test-job:
                  stage: test
                  <<: *maven_image
                  script:
                    - mvn test
                """,
                spec -> spec.path(".gitlab-ci.yml")
            )
        );
    }
    
    @Test 
    void shouldNotModifyNonMatchingImages() {
        rewriteRun(
            yaml(
                """
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/gitleaks/gitlab-ci-gitleaks@2.0
                    inputs:
                      stage: security-scan
                
                test-job:
                  stage: test
                  image: node:18-alpine
                  script:
                    - npm test
                """,
                spec -> spec.path(".gitlab-ci.yml")
            )
        );
    }
}