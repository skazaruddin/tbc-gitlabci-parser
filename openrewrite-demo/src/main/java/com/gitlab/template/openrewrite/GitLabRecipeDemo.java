package com.gitlab.template.openrewrite;

import com.gitlab.template.GitLabTemplateParser;
import com.gitlab.template.model.GitLabTemplate;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.yaml.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Demonstration class showing how to use OpenRewrite recipes with GitLab Template Parser.
 * This class provides examples of applying recipes programmatically.
 */
public class GitLabRecipeDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(GitLabRecipeDemo.class);
    
    public static void main(String[] args) {
        GitLabRecipeDemo demo = new GitLabRecipeDemo();
        
        try {
            // Demonstrate Maven Java 11 to 17 upgrade
            demo.demonstrateMavenUpgrade();
            
            // Demonstrate SonarQube upgrade
            demo.demonstrateSonarQubeUpgrade();
            
            // Demonstrate direct GitLab parser usage
            demo.demonstrateDirectParserUsage();
            
        } catch (Exception e) {
            logger.error("Demo execution failed", e);
            System.exit(1);
        }
    }
    
    /**
     * Demonstrates updating Maven images from Java 11 to Java 17.
     */
    public void demonstrateMavenUpgrade() throws IOException {
        logger.info("=== Demonstrating Maven Java 11 to 17 Upgrade ===");
        
        String yamlContent = """
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "maven:3.8-openjdk-11"
                      deploy-enabled: true
                
                test-job:
                  stage: test
                  image: maven:3.8-openjdk-11
                  script:
                    - mvn test
                """;
        
        Recipe recipe = new UpdateGitLabImageTags()
                .withComponentPath("maven")
                .withNewImage("maven:3.8-openjdk-17");
        
        String result = applyRecipe(recipe, yamlContent, ".gitlab-ci.yml");
        
        logger.info("BEFORE:");
        logger.info(yamlContent);
        logger.info("AFTER:");
        logger.info(result);
    }
    
    /**
     * Demonstrates updating SonarQube images to latest.
     */
    public void demonstrateSonarQubeUpgrade() throws IOException {
        logger.info("=== Demonstrating SonarQube Upgrade to Latest ===");
        
        String yamlContent = """
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/sonarqube/gitlab-ci-sonarqube@3.0
                    inputs:
                      image: "sonarqube:9.9-community"
                      stage: code-quality
                
                sonar-scan:
                  stage: code-quality
                  image: sonarqube:9.9-community
                  script:
                    - sonar-scanner
                """;
        
        Recipe recipe = new UpdateGitLabImageTags()
                .withComponentPath("sonarqube")
                .withNewImage("sonarqube:latest");
        
        String result = applyRecipe(recipe, yamlContent, ".gitlab-ci.yml");
        
        logger.info("BEFORE:");
        logger.info(yamlContent);
        logger.info("AFTER:");
        logger.info(result);
    }
    
    /**
     * Demonstrates direct usage of GitLab Template Parser without OpenRewrite.
     */
    public void demonstrateDirectParserUsage() throws Exception {
        logger.info("=== Demonstrating Direct GitLab Parser Usage ===");
        
        String yamlContent = """
                .maven_image: &maven_image
                  image: maven:3.8-openjdk-11
                
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "maven:3.8-openjdk-11"
                
                test-job:
                  stage: test
                  image: !reference [.maven_image, image]
                  script:
                    - mvn test
                  <<: *maven_image
                """;
        
        GitLabTemplateParser parser = new GitLabTemplateParser();
        GitLabTemplate template = parser.parseFromString(yamlContent);
        
        logger.info("=== Template Analysis ===");
        logger.info("Has TBC components: {}", template.hasTBCComponents());
        logger.info("Maven components found: {}", template.getComponentsByPath("maven").size());
        logger.info("All image references: {}", template.getAllImageReferences());
        
        GitLabTemplate updated = parser.updateImageInSection(template, "maven", "maven:3.8-openjdk-17");
        String updatedYaml = parser.dumpToString(updated);
        
        logger.info("BEFORE:");
        logger.info(yamlContent);
        logger.info("AFTER:");
        logger.info(updatedYaml);
    }
    
    /**
     * Helper method to apply OpenRewrite recipe to YAML content.
     */
    private String applyRecipe(Recipe recipe, String yamlContent, String fileName) throws IOException {
        YamlParser parser = new YamlParser();
        List<SourceFile> sources = parser.parse(yamlContent).toList();
        
        if (sources.isEmpty()) {
            throw new RuntimeException("Failed to parse YAML content");
        }
        
        SourceFile sourceFile = sources.get(0).withSourcePath(Paths.get(fileName));
        ExecutionContext ctx = new InMemoryExecutionContext();
        
        // Use the recipe's visitor directly
        SourceFile result = (SourceFile) recipe.getVisitor().visit(sourceFile, ctx);
        
        if (result == null || result == sourceFile) {
            return yamlContent; // No changes made
        }
        
        return result.printAll();
    }
}