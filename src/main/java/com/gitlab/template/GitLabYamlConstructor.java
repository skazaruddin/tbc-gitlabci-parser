package com.gitlab.template;

import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.constructor.StandardConstructor;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.Tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom YAML constructor that supports GitLab-specific tags like !reference.
 */
public class GitLabYamlConstructor extends StandardConstructor {

    public GitLabYamlConstructor(LoadSettings loadSettings) {
        super(loadSettings);
    }
    
    @Override
    public Object constructObject(Node node) {
        if (node.getTag().equals(new Tag("!reference"))) {
            return constructGitLabReference(node);
        } else if (node.getTag().equals(new Tag("!include"))) {
            return constructGitLabInclude(node);
        }
        return super.constructObject(node);
    }

    /**
     * Constructor for !reference [anchor, property] tags.
     * Converts GitLab !reference tags to a structured representation.
     */
    private Object constructGitLabReference(Node node) {
        if (!(node instanceof SequenceNode)) {
            throw new IllegalArgumentException("!reference must be followed by a sequence");
        }
        
        SequenceNode sequenceNode = (SequenceNode) node;
        List<Object> values = constructSequence(sequenceNode);
        
        if (values.size() != 2) {
            throw new IllegalArgumentException("!reference must have exactly 2 elements: [anchor, property]");
        }
        
        // Create a structured representation of the reference
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("__gitlab_reference", true);
        reference.put("anchor", values.get(0));
        reference.put("property", values.get(1));
        
        return reference;
    }

    /**
     * Constructor for !include tags.
     * Preserves include references as structured data.
     */
    private Object constructGitLabInclude(Node node) {
        Object value;
        if (node instanceof ScalarNode) {
            value = constructScalar((ScalarNode) node);
        } else if (node instanceof SequenceNode) {
            value = constructSequence((SequenceNode) node);
        } else {
            value = constructObject(node);
        }
        
        // Create a structured representation of the include
        Map<String, Object> include = new LinkedHashMap<>();
        include.put("__gitlab_include", true);
        include.put("value", value);
        
        return include;
    }
}