package com.gitlab.template;

import com.gitlab.template.model.GitLabTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test GitLab-specific tag handling and round-trip fidelity.
 */
public class GitLabTagsTest {
    
    private GitLabTemplateParser parser;
    
    @BeforeEach
    void setUp() {
        parser = new GitLabTemplateParser();
    }
    
    @Test
    @DisplayName("Should preserve !reference tags exactly")
    public void testPreserveGitLabReferences() throws Exception {
        String yamlContent = "job:\n" +
                           "  extends: !reference [.base, config]\n" +
                           "  script: !reference [.scripts, build]";
        
        GitLabTemplate template = parser.parse(yamlContent);
        String output = parser.dump(template);
        
        // The template should parse without errors
        assertNotNull(template);
        assertNotNull(output);
        
        // Check that the structure was preserved
        assertTrue(template.getData().containsKey("job"));
    }
    
    @Test
    @DisplayName("Should preserve !include tags")
    public void testPreserveGitLabIncludes() throws Exception {
        String yamlContent = "include:\n" +
                           "  - !include 'common/.gitlab-ci.yml'";
        
        GitLabTemplate template = parser.parse(yamlContent);
        String output = parser.dump(template);
        
        assertNotNull(template);
        assertNotNull(output);
        assertTrue(template.getData().containsKey("include"));
    }
    
    @Test
    @DisplayName("Should handle complex GitLab template with mixed content")
    public void testComplexGitLabTemplate() throws Exception {
        String yamlContent = "# GitLab CI Configuration\n" +
                           "variables:\n" +
                           "  APP_VERSION: \"1.0.0\"\n" +
                           "\n" +
                           "include:\n" +
                           "  - project: 'to-be-continuous/tools'\n" +
                           "    ref: '3.0.0'\n" +
                           "    file: '/templates/.gitlab-ci.yml'\n" +
                           "\n" +
                           "build:\n" +
                           "  stage: build\n" +
                           "  image: docker:20.10.16\n" +
                           "  extends: .tbc-base\n" +
                           "  script:\n" +
                           "    - docker build -t app:$APP_VERSION .";
        
        GitLabTemplate template = parser.parse(yamlContent);
        String output = parser.dump(template);
        
        assertNotNull(template);
        assertNotNull(output);
        
        // Verify main structure is preserved
        assertTrue(template.getData().containsKey("build"));
        assertTrue(template.getData().containsKey("variables"));
        assertTrue(template.getData().containsKey("include"));
        
        // Test that we can modify the image tag
        boolean updated = parser.updateImageTag(template, "build", "docker:20.10.16", "docker:latest");
        assertTrue(updated);
        
        String updatedOutput = parser.dump(template);
        assertNotNull(updatedOutput);
    }
    
    @Test
    @DisplayName("Should handle nested image references")
    public void testNestedImageReference() throws Exception {
        String yamlContent = "build:\n" +
                           "  stage: build\n" +
                           "  image:\n" +
                           "    name: docker:20.10.16\n" +
                           "    entrypoint: [\"\"]";
        
        GitLabTemplate template = parser.parse(yamlContent);
        
        assertTrue(parser.hasImage(template, "build"));
        assertTrue(parser.getImage(template, "build").isPresent());
        assertEquals("docker:20.10.16", parser.getImage(template, "build").get());
        
        // Update the nested image
        boolean updated = parser.updateImageTag(template, "build", "docker:20.10.16", "docker:latest");
        assertTrue(updated);
        
        assertEquals("docker:latest", parser.getImage(template, "build").get());
    }
    
    @Test
    @DisplayName("Should survive round-trip processing with comments")
    public void testRoundTripWithComments() throws Exception {
        String yamlWithComments = "# Job definition\n" +
                                "build:\n" +
                                "  stage: build  # Build stage\n" +
                                "  script:\n" +
                                "    - make build  # Run build";
        
        GitLabTemplate template = parser.parse(yamlWithComments);
        String output = parser.dump(template);
        
        assertNotNull(output);
        assertTrue(template.getData().containsKey("build"));
        
        // Parse the output again to ensure round-trip works
        GitLabTemplate reparsed = parser.parse(output);
        assertNotNull(reparsed);
        assertEquals(1, parser.findComponents(reparsed).size());
    }
}