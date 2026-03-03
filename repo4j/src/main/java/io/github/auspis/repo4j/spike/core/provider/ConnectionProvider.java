package io.github.auspis.repo4j.spike.core.provider;

import java.sql.Connection;

/**
 * Interface for managing the lifecycle of a Connection.
 * Implementations can use different mechanisms (ThreadLocal, ScopedValue, etc.)
 * to store and retrieve connections.
 */
public interface ConnectionProvider {

    /**
     * Sets the Connection for the current context.
     *
     * @param connection the Connection to manage
     * @throws IllegalArgumentException if connection is null
     */
    void setConnection(Connection connection);

    /**
     * Retrieves the Connection for the current context.
     *
     * @return the managed Connection
     * @throws IllegalStateException if no Connection has been set
     */
    Connection getConnection();

    /**
     * Checks if a Connection is available for the current context.
     *
     * @return true if a Connection is set, false otherwise
     */
    boolean hasConnection();

    /**
     * Closes and removes the Connection from the current context.
     */
    void close();

    /**
     * Clears the Connection from the current context without closing it.
     * Useful if the Connection is managed externally.
     */
    void clear();
}
