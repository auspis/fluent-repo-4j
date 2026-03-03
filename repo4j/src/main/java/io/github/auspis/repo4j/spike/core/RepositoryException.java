package io.github.auspis.repo4j.spike.core;

/**
 * Unchecked exception for repository errors.
 * Wraps SQLException and other low-level exceptions.
 */
public class RepositoryException extends RuntimeException {
    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }

    public RepositoryException(Throwable cause) {
        super(cause);
    }
}
