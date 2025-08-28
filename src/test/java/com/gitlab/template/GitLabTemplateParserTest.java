package com.gitlab.template;

import com.gitlab.template.exception.GitLabParseException;
import com.gitlab.template.model.GitLabTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for GitLabTemplateParser functionality.
 */
public class GitLabTemplateParserTest {
    
    private GitLabTemplateParser parser;
    
    @BeforeEach
    void setUp() {
        parser = new GitLabTemplateParser();
    }
    
    @Test
    @DisplayName("Should parse simple GitLab CI template")
    public void testParseSimpleTemplate() throws Exception {
        String yamlContent = "build:\n" +
                           "  stage: build\n" +
                           "  image: docker:20.10.16\n" +
                           "  script:\n" +
                           "    - docker build .";
        
        GitLabTemplate template = parser.parse(yamlContent);
        
        assertNotNull(template);
        assertNotNull(template.getData());
        assertNotNull(template.getRootNode());
    }
    
    @Test
    @DisplayName("Should find components in template")
    public void testFindComponents() throws Exception {
        String yamlContent = "variables:\n" +
                           "  APP_VERSION: \"1.0.0\"\n" +
                           "\n" +
                           "build:\n" +
                           "  stage: build\n" +
                           "  image: docker:20.10.16\n" +
                           "  \n" +
                           "test:\n" +
                           "  stage: test\n" +
                           "  image: node:18";
        
        GitLabTemplate template = parser.parse(yamlContent);
        List<String> components = parser.findComponents(template);
        
        assertEquals(2, components.size());
        assertTrue(components.contains("build"));
        assertTrue(components.contains("test"));
        assertFalse(components.contains("variables")); // Should not include global sections
    }
    
    @Test
    @DisplayName("Should detect image in component")
    public void testHasImage() throws Exception {
        String yamlContent = "build:\n" +
                           "  stage: build\n" +
                           "  image: docker:20.10.16\n" +
                           "  \n" +
                           "no-image:\n" +
                           "  stage: test\n" +
                           "  script:\n" +
                           "    - echo \"test\"";
        
        GitLabTemplate template = parser.parse(yamlContent);
        
        assertTrue(parser.hasImage(template, "build"));
        assertFalse(parser.hasImage(template, "no-image"));
    }
    
    @Test
    @DisplayName("Should get image from component")
    public void testGetImage() throws Exception {
        String yamlContent = "build:\n" +
                           "  stage: build\n" +
                           "  image: docker:20.10.16";
        
        GitLabTemplate template = parser.parse(yamlContent);
        Optional<String> image = parser.getImage(template, "build");
        
        assertTrue(image.isPresent());
        assertEquals("docker:20.10.16", image.get());
    }
    
    @Test
    @DisplayName("Should update image tag")
    public void testUpdateImageTag() throws Exception {
        String yamlContent = "build:\n" +
                           "  stage: build\n" +
                           "  image: docker:20.10.16\n" +
                           "  script:\n" +
                           "    - docker build .";
        
        GitLabTemplate template = parser.parse(yamlContent);
        boolean updated = parser.updateImageTag(template, "build", "docker:20.10.16", "docker:latest");
        
        assertTrue(updated);
        Optional<String> newImage = parser.getImage(template, "build");
        assertTrue(newImage.isPresent());
        assertEquals("docker:latest", newImage.get());
    }
    
    @Test
    @DisplayName("Should handle round-trip parsing")
    public void testRoundTrip() throws Exception {
        String yamlContent = "# Main GitLab CI configuration\n" +
                           "variables:\n" +
                           "  APP_VERSION: \"1.0.0\"\n" +
                           "\n" +
                           "build:\n" +
                           "  stage: build\n" +
                           "  image: docker:20.10.16\n" +
                           "  script:\n" +
                           "    - docker build .";
        
        GitLabTemplate template = parser.parse(yamlContent);
        String output = parser.dump(template);
        
        assertNotNull(output);
        assertTrue(output.contains("build:"));
        assertTrue(output.contains("docker:20.10.16"));
    }
    
    @Test
    @DisplayName("Should throw exception for null content")
    public void testParseNullContent() {
        assertThrows(GitLabParseException.class, () -> parser.parse(null));
        assertThrows(GitLabParseException.class, () -> parser.parse(""));
        assertThrows(GitLabParseException.class, () -> parser.parse("   "));
    }
}