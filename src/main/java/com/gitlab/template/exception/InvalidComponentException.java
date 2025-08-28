package com.gitlab.template.exception;

/**
 * Exception thrown when operations on invalid components are attempted.
 */
public class InvalidComponentException extends Exception {
    
    public InvalidComponentException(String message) {
        super(message);
    }
    
    public InvalidComponentException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public InvalidComponentException(Throwable cause) {
        super(cause);
    }
}