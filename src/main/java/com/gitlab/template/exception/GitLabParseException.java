package com.gitlab.template.exception;

/**
 * Exception thrown when GitLab CI/CD template parsing fails.
 */
public class GitLabParseException extends Exception {
    
    public GitLabParseException(String message) {
        super(message);
    }
    
    public GitLabParseException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public GitLabParseException(Throwable cause) {
        super(cause);
    }
}