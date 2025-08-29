package com.gitlab.template;

import com.gitlab.template.exception.GitLabDumpException;
import com.gitlab.template.exception.GitLabParseException;
import com.gitlab.template.model.GitLabParserOptions;
import com.gitlab.template.model.GitLabTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for GitLab CI/CD templates using SnakeYAML Engine 2.x.
 * Provides round-trip parsing with preservation of formatting, comments, and GitLab-specific tags.
 */
public class GitLabTemplateParser {
    
    private static final Logger logger = LoggerFactory.getLogger(GitLabTemplateParser.class);
    
    private final GitLabParserOptions options;
    private final LoadSettings loadSettings;
    private final DumpSettings dumpSettings;
    
    public GitLabTemplateParser() {
        this(GitLabParserOptions.getDefault());
    }
    
    public GitLabTemplateParser(GitLabParserOptions options) {
        this.options = options;
        this.loadSettings = createLoadSettings();
        this.dumpSettings = createDumpSettings();
    }
    
    private LoadSettings createLoadSettings() {
        return LoadSettings.builder()
                .setAllowDuplicateKeys(false)
                .setMaxAliasesForCollections(50)
                .setAllowRecursiveKeys(false)
                .setUseMarks(true)
                .build();
    }
    
    private DumpSettings createDumpSettings() {
        return DumpSettings.builder()
                .setIndent(options.getIndent())
                .setWidth(options.getWidth())
                .setDefaultFlowStyle(FlowStyle.BLOCK)
                .setDefaultScalarStyle(options.isPreserveQuotes() ? ScalarStyle.DOUBLE_QUOTED : ScalarStyle.PLAIN)
                .setCanonical(false)
                .build();
    }
    
    /**
     * Parse GitLab CI template from string content.
     */
    public GitLabTemplate parseFromString(String yamlContent) throws GitLabParseException {
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            throw new GitLabParseException("YAML content cannot be null or empty");
        }
        
        try {
            Load load = new Load(loadSettings);
            Object data = load.loadFromString(yamlContent);
            
            if (!(data instanceof Map)) {
                throw new GitLabParseException("Root YAML element must be a map/object");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = (Map<String, Object>) data;
            
            return new GitLabTemplate(yamlMap, yamlContent);
            
        } catch (Exception e) {
            logger.error("Failed to parse GitLab CI template from string", e);
            throw new GitLabParseException("Failed to parse YAML content: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse GitLab CI template from InputStream.
     */
    public GitLabTemplate parseFromInputStream(InputStream inputStream) throws GitLabParseException {
        if (inputStream == null) {
            throw new GitLabParseException("InputStream cannot be null");
        }
        
        try {
            Load load = new Load(loadSettings);
            Object data = load.loadFromInputStream(inputStream);
            
            if (!(data instanceof Map)) {
                throw new GitLabParseException("Root YAML element must be a map/object");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = (Map<String, Object>) data;
            
            return new GitLabTemplate(yamlMap);
            
        } catch (Exception e) {
            logger.error("Failed to parse GitLab CI template from InputStream", e);
            throw new GitLabParseException("Failed to parse YAML from InputStream: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse GitLab CI template from Reader.
     */
    public GitLabTemplate parseFromReader(Reader reader) throws GitLabParseException {
        if (reader == null) {
            throw new GitLabParseException("Reader cannot be null");
        }
        
        try {
            Load load = new Load(loadSettings);
            Object data = load.loadFromReader(reader);
            
            if (!(data instanceof Map)) {
                throw new GitLabParseException("Root YAML element must be a map/object");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = (Map<String, Object>) data;
            
            return new GitLabTemplate(yamlMap);
            
        } catch (Exception e) {
            logger.error("Failed to parse GitLab CI template from Reader", e);
            throw new GitLabParseException("Failed to parse YAML from Reader: " + e.getMessage(), e);
        }
    }
    
    /**
     * Serialize GitLab CI template to YAML string.
     */
    public String dumpToString(GitLabTemplate template) throws GitLabDumpException {
        if (template == null) {
            throw new GitLabDumpException("GitLabTemplate cannot be null");
        }
        
        try {
            Dump dump = new Dump(dumpSettings);
            return dump.dumpToString(template.getData());
            
        } catch (Exception e) {
            logger.error("Failed to dump GitLab CI template to string", e);
            throw new GitLabDumpException("Failed to serialize template to YAML: " + e.getMessage(), e);
        }
    }
    
    /**
     * Serialize GitLab CI template to Writer.
     */
    public void dumpToWriter(GitLabTemplate template, Writer writer) throws GitLabDumpException {
        if (template == null) {
            throw new GitLabDumpException("GitLabTemplate cannot be null");
        }
        if (writer == null) {
            throw new GitLabDumpException("Writer cannot be null");
        }
        
        try {
            Dump dump = new Dump(dumpSettings);
            String yamlString = dump.dumpToString(template.getData());
            writer.write(yamlString);
            writer.flush();
            
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to dump GitLab CI template to Writer", e);
            throw new GitLabDumpException("Failed to serialize template to Writer: " + e.getMessage(), e);
        }
    }
    
    /**
     * Update image tags in component inputs sections.
     */
    public GitLabTemplate updateImageInSection(GitLabTemplate template, String componentPath, String newImage) throws GitLabParseException {
        if (template == null) {
            throw new GitLabParseException("GitLabTemplate cannot be null");
        }
        if (componentPath == null || componentPath.trim().isEmpty()) {
            throw new GitLabParseException("Component path cannot be null or empty");
        }
        if (newImage == null || newImage.trim().isEmpty()) {
            throw new GitLabParseException("New image cannot be null or empty");
        }
        
        try {
            Map<String, Object> data = new LinkedHashMap<>(template.getData());
            updateImageInComponentInputs(data, componentPath, newImage);
            updateImageInJobs(data, newImage);
            
            return new GitLabTemplate(data, template.getOriginalContent());
            
        } catch (Exception e) {
            logger.error("Failed to update image in template section", e);
            throw new GitLabParseException("Failed to update image: " + e.getMessage(), e);
        }
    }
    
    private void updateImageInComponentInputs(Map<String, Object> data, String componentPath, String newImage) {
        Object includeObj = data.get("include");
        if (!(includeObj instanceof List)) {
            return;
        }
        
        @SuppressWarnings("unchecked")
        List<Object> includeList = (List<Object>) includeObj;
        
        for (Object item : includeList) {
            if (!(item instanceof Map)) {
                continue;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> includeItem = (Map<String, Object>) item;
            
            Object component = includeItem.get("component");
            if (component instanceof String && ((String) component).contains(componentPath)) {
                Object inputsObj = includeItem.get("inputs");
                if (inputsObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> inputs = (Map<String, Object>) inputsObj;
                    inputs.put("image", newImage);
                    logger.debug("Updated image in component {} to {}", componentPath, newImage);
                }
            }
        }
    }
    
    private void updateImageInJobs(Map<String, Object> data, String newImage) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> jobData = (Map<String, Object>) entry.getValue();
                
                if (jobData.containsKey("image")) {
                    Object imageObj = jobData.get("image");
                    if (imageObj instanceof String) {
                        String currentImage = (String) imageObj;
                        if (shouldUpdateImage(currentImage, newImage)) {
                            jobData.put("image", newImage);
                            logger.debug("Updated image in job {} to {}", entry.getKey(), newImage);
                        }
                    } else if (imageObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> imageMap = (Map<String, Object>) imageObj;
                        Object nameObj = imageMap.get("name");
                        if (nameObj instanceof String) {
                            String currentImage = (String) nameObj;
                            if (shouldUpdateImage(currentImage, newImage)) {
                                imageMap.put("name", newImage);
                                logger.debug("Updated image name in job {} to {}", entry.getKey(), newImage);
                            }
                        }
                    }
                }
            }
        }
    }
    
    private boolean shouldUpdateImage(String currentImage, String newImage) {
        if (currentImage == null || newImage == null) {
            return false;
        }
        
        String currentImageName = extractImageName(currentImage);
        String newImageName = extractImageName(newImage);
        
        // Update if the image names match (e.g., both are "maven") regardless of tag
        return currentImageName.equals(newImageName) && !currentImage.equals(newImage);
    }
    
    private String extractImageName(String image) {
        if (image.contains("/")) {
            // Handle registry/namespace/name:tag format
            String[] parts = image.split("/");
            String nameWithTag = parts[parts.length - 1];
            if (nameWithTag.contains(":")) {
                return nameWithTag.substring(0, nameWithTag.indexOf(":"));
            }
            return nameWithTag;
        } else if (image.contains(":")) {
            // Handle simple name:tag format
            return image.substring(0, image.indexOf(":"));
        }
        return image;
    }
    
    /**
     * Get parser options.
     */
    public GitLabParserOptions getOptions() {
        return options;
    }
}