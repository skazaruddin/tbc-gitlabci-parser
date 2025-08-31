package com.gitlab.template.openrewrite;

import com.gitlab.template.GitLabTemplateParser;
import com.gitlab.template.exception.GitLabParseException;
import com.gitlab.template.exception.GitLabDumpException;
import com.gitlab.template.model.GitLabTemplate;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenRewrite recipe that updates GitLab CI image tags using the GitLab Template Parser.
 * This recipe demonstrates how to integrate the GitLab parser with OpenRewrite for 
 * automated YAML modifications.
 */
public class UpdateGitLabImageTags extends Recipe {
    
    private static final Logger logger = LoggerFactory.getLogger(UpdateGitLabImageTags.class);
    
    @Option(displayName = "Component path",
            description = "The component path to match (e.g., 'maven', 'gitleaks')",
            example = "maven")
    String componentPath;
    
    @Option(displayName = "New image",
            description = "The new Docker image to use",
            example = "maven:3.8-openjdk-17")
    String newImage;
    
    @Option(displayName = "File pattern",
            description = "File pattern to match GitLab CI files",
            example = "**/.gitlab-ci.yml")
    String filePattern = "**/.gitlab-ci.yml";
    
    public UpdateGitLabImageTags withComponentPath(String componentPath) {
        this.componentPath = componentPath;
        return this;
    }
    
    public UpdateGitLabImageTags withNewImage(String newImage) {
        this.newImage = newImage;
        return this;
    }
    
    public UpdateGitLabImageTags withFilePattern(String filePattern) {
        this.filePattern = filePattern;
        return this;
    }
    
    @Override
    public String getDisplayName() {
        return "Update GitLab CI image tags";
    }
    
    @Override
    public String getDescription() {
        return "Updates Docker image tags in GitLab CI templates using the GitLab Template Parser.";
    }
    
    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                // Check if this looks like a GitLab CI file
                String path = documents.getSourcePath().toString();
                if (!path.endsWith(".gitlab-ci.yml") && !path.endsWith("gitlab-ci.yml")) {
                    return documents;
                }
                
                try {
                    // Get the current YAML content
                    String originalContent = documents.printAll();
                    
                    // Use GitLab Template Parser to parse and modify
                    GitLabTemplateParser parser = new GitLabTemplateParser();
                    GitLabTemplate template = parser.parseFromString(originalContent);
                    
                    // Update the image using the parser
                    GitLabTemplate updatedTemplate = parser.updateImageInSection(template, componentPath, newImage);
                    
                    // Serialize back to YAML
                    String updatedContent = parser.dumpToString(updatedTemplate);
                    
                    // Only return updated content if it actually changed
                    if (!originalContent.equals(updatedContent)) {
                        logger.info("Updated GitLab CI file: {} - component: {}, new image: {}", 
                                   documents.getSourcePath(), componentPath, newImage);
                        
                        // Parse the updated content back into OpenRewrite AST
                        return (Yaml.Documents) new org.openrewrite.yaml.YamlParser().parse(updatedContent)
                                .findFirst()
                                .orElse(documents);
                    }
                    
                    return documents;
                    
                } catch (GitLabParseException | GitLabDumpException e) {
                    logger.error("Failed to process GitLab CI file: {}", documents.getSourcePath(), e);
                    return documents;
                } catch (Exception e) {
                    logger.error("Unexpected error processing GitLab CI file: {}", documents.getSourcePath(), e);
                    return documents;
                }
            }
        };
    }
}