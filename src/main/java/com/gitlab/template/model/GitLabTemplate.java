package com.gitlab.template.model;

import org.yaml.snakeyaml.nodes.Node;
import java.util.Map;

/**
 * Represents a parsed GitLab CI/CD template with preserved structure and metadata.
 */
public class GitLabTemplate {
    
    private final Node rootNode;
    private final Map<String, Object> data;
    private final String originalContent;
    
    public GitLabTemplate(Node rootNode, Map<String, Object> data, String originalContent) {
        this.rootNode = rootNode;
        this.data = data;
        this.originalContent = originalContent;
    }
    
    /**
     * Get the original YAML node structure.
     */
    public Node getRootNode() {
        return rootNode;
    }
    
    /**
     * Get the parsed data structure.
     */
    public Map<String, Object> getData() {
        return data;
    }
    
    /**
     * Get the original content string.
     */
    public String getOriginalContent() {
        return originalContent;
    }
}