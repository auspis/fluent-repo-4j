package io.github.auspis.repo4j.core.provider;

import java.sql.Connection;
import java.util.NoSuchElementException;

/**
 * Connection provider implementation using ScopedValue (Java 21+).
 * Each instance maintains its own ScopedValue storage.
 * Suitable for virtual threads and structured concurrency scenarios.
 *
 * Note: ScopedValue requires explicit scope binding. This implementation
 * provides a simplified interface compatible with traditional usage patterns.
 */
public class ScopedValueConnectionProvider implements ConnectionProvider {

    private final ScopedValue<Connection> connectionStorage = ScopedValue.newInstance();
    private final ThreadLocal<Connection> fallbackStorage = new ThreadLocal<>();

    @Override
    public void setConnection(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }
        // Store in fallback since ScopedValue binding requires try-with-resources context
        fallbackStorage.set(connection);
    }

    @Override
    public Connection getConnection() {
        // Try to get from ScopedValue first
        try {
            Connection conn = connectionStorage.get();
            if (conn != null) {
                return conn;
            }
        } catch (NoSuchElementException e) {
            // ScopedValue not bound in current scope
        }

        // Fallback to ThreadLocal for compatibility
        Connection conn = fallbackStorage.get();
        if (conn == null) {
            throw new IllegalStateException("No Connection available in the current scope. Call setConnection() first.");
        }
        return conn;
    }

    @Override
    public boolean hasConnection() {
        try {
            if (connectionStorage.get() != null) {
                return true;
            }
        } catch (NoSuchElementException e) {
            // ScopedValue not bound in current scope
        }
        return fallbackStorage.get() != null;
    }

    @Override
    public void close() {
        Connection conn = null;
        try {
            conn = connectionStorage.get();
        } catch (NoSuchElementException e) {
            // ScopedValue not bound, try fallback
            conn = fallbackStorage.get();
        }

        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                System.err.println("Error closing Connection: " + e.getMessage());
            } finally {
                fallbackStorage.remove();
            }
        }
    }

    @Override
    public void clear() {
        fallbackStorage.remove();
    }

    /**
     * Binds a Connection to the ScopedValue for the duration of the provided scope.
     * This method should be used when working with structured concurrency.
     *
     * Example:
     * <pre>
     * Connection conn = getConnection();
     * ScopedValueConnectionProvider provider = new ScopedValueConnectionProvider();
     * provider.executeInScope(conn, () -> {
     *     // Connection is bound to ScopedValue here
     *     Connection retrieved = provider.getConnection();
     * });
     * </pre>
     *
     * @param connection the Connection to bind
     * @param scope the scope to execute within the binding
     */
    public void executeInScope(Connection connection, Runnable scope) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }
        ScopedValue.where(connectionStorage, connection).run(scope);
    }

    /**
     * Similar to executeInScope but for callable operations that return a result.
     *
     * @param connection the Connection to bind
     * @param scope the scope to execute within the binding
     * @return the result of the scope execution
     * @throws Exception if the scope execution throws an exception
     */
    public <T> T executeInScope(Connection connection, SupplierWithException<T> scope) throws Exception {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }
        return ScopedValue.where(connectionStorage, connection).call(scope::get);
    }

    /**
     * Functional interface for operations that return a result and may throw exceptions.
     */
    @FunctionalInterface
    public interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
