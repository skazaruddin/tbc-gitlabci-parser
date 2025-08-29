package com.gitlab.template.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a parsed GitLab CI/CD template with its data and metadata.
 */
public class GitLabTemplate {
    
    private final Map<String, Object> data;
    private final String originalContent;
    
    /**
     * Create a GitLab template with data and original content.
     */
    public GitLabTemplate(Map<String, Object> data, String originalContent) {
        this.data = data != null ? new LinkedHashMap<>(data) : new LinkedHashMap<>();
        this.originalContent = originalContent;
    }
    
    /**
     * Create a GitLab template with only data.
     */
    public GitLabTemplate(Map<String, Object> data) {
        this(data, null);
    }
    
    /**
     * Get the template data as a map.
     */
    public Map<String, Object> getData() {
        return Collections.unmodifiableMap(data);
    }
    
    /**
     * Get a mutable copy of the template data.
     */
    public Map<String, Object> getMutableData() {
        return new LinkedHashMap<>(data);
    }
    
    /**
     * Get the original YAML content if available.
     */
    public String getOriginalContent() {
        return originalContent;
    }
    
    /**
     * Get variables section.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getVariables() {
        Object variables = data.get("variables");
        if (variables instanceof Map) {
            return (Map<String, Object>) variables;
        }
        return Collections.emptyMap();
    }
    
    /**
     * Get stages section.
     */
    @SuppressWarnings("unchecked")
    public List<String> getStages() {
        Object stages = data.get("stages");
        if (stages instanceof List) {
            return (List<String>) stages;
        }
        return Collections.emptyList();
    }
    
    /**
     * Get include section.
     */
    @SuppressWarnings("unchecked")
    public List<Object> getInclude() {
        Object include = data.get("include");
        if (include instanceof List) {
            return (List<Object>) include;
        }
        return Collections.emptyList();
    }
    
    /**
     * Get all jobs (entries that are not keywords).
     */
    public Map<String, Object> getJobs() {
        Map<String, Object> jobs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (!isGitLabKeyword(key) && entry.getValue() instanceof Map) {
                jobs.put(key, entry.getValue());
            }
        }
        return jobs;
    }
    
    /**
     * Check if a key is a GitLab CI/CD keyword.
     */
    private boolean isGitLabKeyword(String key) {
        return key.equals("variables") ||
               key.equals("stages") ||
               key.equals("include") ||
               key.equals("image") ||
               key.equals("services") ||
               key.equals("before_script") ||
               key.equals("after_script") ||
               key.equals("cache") ||
               key.equals("workflow") ||
               key.startsWith(".");
    }
    
    /**
     * Get a specific job by name.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getJob(String jobName) {
        Object job = data.get(jobName);
        if (job instanceof Map && !isGitLabKeyword(jobName)) {
            return (Map<String, Object>) job;
        }
        return Collections.emptyMap();
    }
    
    /**
     * Check if template contains a specific job.
     */
    public boolean hasJob(String jobName) {
        return data.containsKey(jobName) && 
               data.get(jobName) instanceof Map && 
               !isGitLabKeyword(jobName);
    }
    
    /**
     * Get component includes by component path pattern.
     */
    public List<Map<String, Object>> getComponentsByPath(String pathPattern) {
        return getInclude().stream()
                .filter(item -> item instanceof Map)
                .map(item -> (Map<String, Object>) item)
                .filter(item -> {
                    Object component = item.get("component");
                    return component instanceof String && 
                           ((String) component).contains(pathPattern);
                })
                .map(item -> (Map<String, Object>) item)
                .collect(Collectors.toList());
    }
    
    /**
     * Check if template has any To-Be-Continuous components.
     */
    public boolean hasTBCComponents() {
        return getInclude().stream()
                .filter(item -> item instanceof Map)
                .map(item -> (Map<String, Object>) item)
                .anyMatch(item -> {
                    Object component = item.get("component");
                    return component instanceof String && 
                           ((String) component).contains("to-be-continuous");
                });
    }
    
    /**
     * Get all image references in the template.
     */
    public Map<String, String> getAllImageReferences() {
        Map<String, String> images = new LinkedHashMap<>();
        
        // Global image
        Object globalImage = data.get("image");
        if (globalImage instanceof String) {
            images.put("global", (String) globalImage);
        }
        
        // Job images
        for (Map.Entry<String, Object> entry : getJobs().entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> job = (Map<String, Object>) entry.getValue();
            Object jobImage = job.get("image");
            if (jobImage instanceof String) {
                images.put("job:" + entry.getKey(), (String) jobImage);
            } else if (jobImage instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> imageMap = (Map<String, Object>) jobImage;
                Object imageName = imageMap.get("name");
                if (imageName instanceof String) {
                    images.put("job:" + entry.getKey(), (String) imageName);
                }
            }
        }
        
        // Component images in inputs
        getInclude().stream()
                .filter(item -> item instanceof Map)
                .map(item -> (Map<String, Object>) item)
                .forEach(item -> {
                    Object component = item.get("component");
                    Object inputs = item.get("inputs");
                    if (component instanceof String && inputs instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> inputsMap = (Map<String, Object>) inputs;
                        Object image = inputsMap.get("image");
                        if (image instanceof String) {
                            images.put("component:" + component, (String) image);
                        }
                    }
                });
        
        return images;
    }
    
    @Override
    public String toString() {
        return "GitLabTemplate{" +
                "dataKeys=" + data.keySet() +
                ", hasOriginalContent=" + (originalContent != null) +
                '}';
    }
}