package io.github.auspis.repo4j.core;

/**
 * Eccezione unchecked per errori del repository.
 * Wrappa SQLException e altre eccezioni di basso livello.
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
