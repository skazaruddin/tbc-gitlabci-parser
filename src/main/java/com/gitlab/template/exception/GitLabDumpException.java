package com.gitlab.template.exception;

/**
 * Exception thrown when GitLab CI/CD template serialization fails.
 */
public class GitLabDumpException extends Exception {
    
    public GitLabDumpException(String message) {
        super(message);
    }
    
    public GitLabDumpException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public GitLabDumpException(Throwable cause) {
        super(cause);
    }
}