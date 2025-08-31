package com.gitlab.template.openrewrite;

import com.gitlab.template.GitLabTemplateParser;
import com.gitlab.template.model.GitLabTemplate;
import org.junit.jupiter.api.Test;

class DebugTest {
    
    @Test
    void debugGitLabParser() throws Exception {
        String yamlContent = """
                include:
                  - component: $CI_SERVER_FQDN/to-be-continuous/maven/gitlab-ci-maven@5.0
                    inputs:
                      image: "docker.io/library/maven:3.8-openjdk-11"
                      deploy-enabled: true
                
                stages:
                  - validate
                  - build
                """;
        
        GitLabTemplateParser parser = new GitLabTemplateParser();
        GitLabTemplate template = parser.parseFromString(yamlContent);
        
        System.out.println("=== BEFORE ===");
        System.out.println(yamlContent);
        
        System.out.println("=== HAS TBC COMPONENTS: " + template.hasTBCComponents() + " ===");
        System.out.println("=== MAVEN COMPONENTS: " + template.getComponentsByPath("maven").size() + " ===");
        
        GitLabTemplate updated = parser.updateImageInSection(template, "maven", "maven:3.8-openjdk-17");
        String updatedYaml = parser.dumpToString(updated);
        
        System.out.println("=== AFTER ===");
        System.out.println(updatedYaml);
        
        System.out.println("=== CHANGED: " + !yamlContent.equals(updatedYaml) + " ===");
    }
}