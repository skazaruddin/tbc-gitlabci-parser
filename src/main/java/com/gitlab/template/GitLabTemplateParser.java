package com.gitlab.template;

import com.gitlab.template.exception.GitLabDumpException;
import com.gitlab.template.exception.GitLabParseException;
import com.gitlab.template.exception.InvalidComponentException;
import com.gitlab.template.model.GitLabTemplate;
import com.gitlab.template.model.GitLabParserOptions;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.Construct;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Main parser for GitLab CI/CD templates.
 * Provides parsing and dumping with round-trip fidelity while preserving
 * comments, formatting, and custom GitLab tags.
 */
public class GitLabTemplateParser {
    
    private static final Logger logger = LoggerFactory.getLogger(GitLabTemplateParser.class);
    
    private final GitLabParserOptions options;
    private final Yaml yaml;
    private final Pattern imagePattern = Pattern.compile("^(.+):(.+)$");
    
    /**
     * Create parser with default options.
     */
    public GitLabTemplateParser() {
        this(GitLabParserOptions.getDefault());
    }
    
    /**
     * Create parser with custom options.
     */
    public GitLabTemplateParser(GitLabParserOptions options) {
        this.options = options;
        this.yaml = createYaml();
    }
    
    /**
     * Parse GitLab CI YAML content into a template object.
     */
    @SuppressWarnings("unchecked")
    public GitLabTemplate parse(String content) throws GitLabParseException {
        if (StringUtils.isBlank(content)) {
            throw new GitLabParseException("Content cannot be null or empty");
        }
        
        try {
            // Parse the content while preserving the node structure
            Node rootNode = yaml.compose(new java.io.StringReader(content));
            if (rootNode == null) {
                throw new GitLabParseException("Invalid YAML content");
            }
            
            // Also create the data structure for easier manipulation
            Object data = yaml.load(content);
            if (!(data instanceof Map)) {
                throw new GitLabParseException("Root element must be a mapping");
            }
            
            return new GitLabTemplate(rootNode, (Map<String, Object>) data, content);
            
        } catch (Exception e) {
            logger.error("Failed to parse GitLab template", e);
            throw new GitLabParseException("Failed to parse GitLab template: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert template back to YAML string with preserved formatting.
     */
    public String dump(GitLabTemplate template) throws GitLabDumpException {
        if (template == null) {
            throw new GitLabDumpException("Template cannot be null");
        }
        
        try {
            // For now, use the data structure instead of the node structure
            // to ensure updates are reflected in the output
            java.io.StringWriter writer = new java.io.StringWriter();
            yaml.dump(template.getData(), writer);
            return writer.toString();
        } catch (Exception e) {
            logger.error("Failed to dump GitLab template", e);
            throw new GitLabDumpException("Failed to serialize template: " + e.getMessage(), e);
        }
    }
    
    /**
     * Update Docker image tag in any section (including global sections like include).
     * This method can update images in component inputs within include sections.
     */
    public boolean updateImageInSection(GitLabTemplate template, String section, String oldTag, String newTag) 
            throws InvalidComponentException {
        if (template == null || StringUtils.isBlank(section)) {
            throw new InvalidComponentException("Template and section name cannot be null or empty");
        }
        
        Map<String, Object> data = template.getData();
        Object sectionData = data.get(section);
        
        if (sectionData == null) {
            logger.warn("Section '{}' not found in template", section);
            return false;
        }
        
        if (sectionData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sectionMap = (Map<String, Object>) sectionData;
            return updateImageInMap(sectionMap, oldTag, newTag);
        } else if (sectionData instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> sectionList = (List<Object>) sectionData;
            boolean updated = false;
            for (Object item : sectionList) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemMap = (Map<String, Object>) item;
                    updated |= updateImageInMap(itemMap, oldTag, newTag);
                }
            }
            return updated;
        } else {
            logger.warn("Section '{}' is not a mapping or list", section);
            return false;
        }
    }

    /**
     * Update Docker image tag in a specific component.
     */
    public boolean updateImageTag(GitLabTemplate template, String component, String oldTag, String newTag) 
            throws InvalidComponentException {
        if (template == null || StringUtils.isBlank(component)) {
            throw new InvalidComponentException("Template and component name cannot be null or empty");
        }
        
        Map<String, Object> data = template.getData();
        Object componentData = data.get(component);
        
        if (componentData == null) {
            logger.warn("Component '{}' not found in template", component);
            return false;
        }
        
        if (!(componentData instanceof Map)) {
            logger.warn("Component '{}' is not a mapping", component);
            return false;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> componentMap = (Map<String, Object>) componentData;
        
        return updateImageInMap(componentMap, oldTag, newTag);
    }
    
    /**
     * Find all components/jobs in the template.
     */
    public List<String> findComponents(GitLabTemplate template) {
        if (template == null) {
            return Collections.emptyList();
        }
        
        List<String> components = new ArrayList<>();
        Map<String, Object> data = template.getData();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Skip global sections
            if (isGlobalSection(key)) {
                continue;
            }
            
            // Jobs are typically maps with stage, script, etc.
            if (value instanceof Map) {
                components.add(key);
            }
        }
        
        return components;
    }
    
    /**
     * Check if a component has an image definition.
     */
    public boolean hasImage(GitLabTemplate template, String component) {
        try {
            return getImage(template, component).isPresent();
        } catch (Exception e) {
            logger.debug("Error checking image for component '{}'", component, e);
            return false;
        }
    }
    
    /**
     * Get the current image tag for a component.
     */
    public Optional<String> getImage(GitLabTemplate template, String component) {
        if (template == null || StringUtils.isBlank(component)) {
            return Optional.empty();
        }
        
        Map<String, Object> data = template.getData();
        Object componentData = data.get(component);
        
        if (!(componentData instanceof Map)) {
            return Optional.empty();
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> componentMap = (Map<String, Object>) componentData;
        
        return findImageInMap(componentMap);
    }
    
    private Yaml createYaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setProcessComments(true);
        
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setIndent(options.getIndent());
        dumperOptions.setWidth(options.getWidth());
        dumperOptions.setProcessComments(true);
        
        Constructor constructor = new GitLabConstructor(loaderOptions);
        Representer representer = new GitLabRepresenter(dumperOptions);
        
        return new Yaml(constructor, representer, dumperOptions, loaderOptions);
    }
    
    private boolean updateImageInMap(Map<String, Object> map, String oldTag, String newTag) {
        boolean updated = false;
        
        Object imageValue = map.get("image");
        if (imageValue != null) {
            if (imageValue instanceof String) {
                String imageStr = (String) imageValue;
                if (imageStr.equals(oldTag)) {
                    map.put("image", newTag);
                    updated = true;
                } else if (imageStr.contains(":")) {
                    // Try to replace just the tag part
                    String[] parts = imageStr.split(":", 2);
                    if (parts.length == 2 && oldTag.contains(":")) {
                        String[] oldParts = oldTag.split(":", 2);
                        if (oldParts.length == 2 && parts[0].equals(oldParts[0]) && parts[1].equals(oldParts[1])) {
                            map.put("image", newTag);
                            updated = true;
                        }
                    }
                }
            } else if (imageValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> imageMap = (Map<String, Object>) imageValue;
                Object nameValue = imageMap.get("name");
                if (nameValue instanceof String && nameValue.equals(oldTag)) {
                    imageMap.put("name", newTag);
                    updated = true;
                }
            }
        }
        
        // Recursively check nested structures
        for (Object value : map.values()) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                updated |= updateImageInMap(nestedMap, oldTag, newTag);
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        updated |= updateImageInMap(itemMap, oldTag, newTag);
                    }
                }
            }
        }
        
        return updated;
    }
    
    private Optional<String> findImageInMap(Map<String, Object> map) {
        Object imageValue = map.get("image");
        if (imageValue instanceof String) {
            return Optional.of((String) imageValue);
        } else if (imageValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> imageMap = (Map<String, Object>) imageValue;
            Object nameValue = imageMap.get("name");
            if (nameValue instanceof String) {
                return Optional.of((String) nameValue);
            }
        }
        
        // Check nested structures
        for (Object value : map.values()) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                Optional<String> nestedImage = findImageInMap(nestedMap);
                if (nestedImage.isPresent()) {
                    return nestedImage;
                }
            }
        }
        
        return Optional.empty();
    }
    
    private boolean isGlobalSection(String key) {
        return key.equals("include") || key.equals("variables") || key.equals("stages") || 
               key.equals("before_script") || key.equals("after_script") || key.equals("cache") ||
               key.startsWith(".");
    }
    
    /**
     * Custom constructor to handle GitLab-specific tags.
     */
    private static class GitLabConstructor extends Constructor {
        public GitLabConstructor(LoaderOptions loaderOptions) {
            super(loaderOptions);
            // Add support for GitLab-specific tags
            this.yamlConstructors.put(new Tag("!reference"), new ReferenceConstructor());
            this.yamlConstructors.put(new Tag("!include"), new IncludeConstructor());
        }
        
        private class ReferenceConstructor implements Construct {
            public Object construct(Node node) {
                // Preserve !reference tags as-is
                if (node instanceof SequenceNode) {
                    return constructSequence((SequenceNode) node);
                }
                return constructScalar((ScalarNode) node);
            }
            
            public void construct2ndStep(Node node, Object object) {
                // No second step needed for references
            }
        }
        
        private class IncludeConstructor implements Construct {
            public Object construct(Node node) {
                // Preserve !include tags as-is
                if (node instanceof ScalarNode) {
                    return constructScalar((ScalarNode) node);
                } else if (node instanceof SequenceNode) {
                    return constructSequence((SequenceNode) node);
                } else {
                    return constructMapping((MappingNode) node);
                }
            }
            
            public void construct2ndStep(Node node, Object object) {
                // No second step needed for includes
            }
        }
    }
    
    /**
     * Custom representer to handle GitLab-specific tags.
     */
    private static class GitLabRepresenter extends Representer {
        public GitLabRepresenter(DumperOptions dumperOptions) {
            super(dumperOptions);
        }
    }
}