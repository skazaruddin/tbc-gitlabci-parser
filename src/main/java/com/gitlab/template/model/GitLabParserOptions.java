package com.gitlab.template.model;

/**
 * Configuration options for GitLab template parsing.
 */
public class GitLabParserOptions {
    
    private final boolean preserveQuotes;
    private final boolean preserveOrder;
    private final int indent;
    private final int width;
    
    private GitLabParserOptions(Builder builder) {
        this.preserveQuotes = builder.preserveQuotes;
        this.preserveOrder = builder.preserveOrder;
        this.indent = builder.indent;
        this.width = builder.width;
    }
    
    public boolean isPreserveQuotes() {
        return preserveQuotes;
    }
    
    public boolean isPreserveOrder() {
        return preserveOrder;
    }
    
    public int getIndent() {
        return indent;
    }
    
    public int getWidth() {
        return width;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private boolean preserveQuotes = true;
        private boolean preserveOrder = true;
        private int indent = 2;
        private int width = 120;
        
        public Builder preserveQuotes(boolean preserveQuotes) {
            this.preserveQuotes = preserveQuotes;
            return this;
        }
        
        public Builder preserveOrder(boolean preserveOrder) {
            this.preserveOrder = preserveOrder;
            return this;
        }
        
        public Builder indent(int indent) {
            this.indent = indent;
            return this;
        }
        
        public Builder width(int width) {
            this.width = width;
            return this;
        }
        
        public GitLabParserOptions build() {
            return new GitLabParserOptions(this);
        }
    }
    
    /**
     * Creates default parser options.
     */
    public static GitLabParserOptions getDefault() {
        return builder().build();
    }
}