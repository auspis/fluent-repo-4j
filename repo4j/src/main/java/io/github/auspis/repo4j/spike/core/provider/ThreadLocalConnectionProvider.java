package io.github.auspis.repo4j.spike.core.provider;

import java.sql.Connection;

/**
 * Connection provider implementation using ThreadLocal.
 * Each instance maintains its own ThreadLocal storage.
 * Suitable for traditional thread pool scenarios.
 */
public class ThreadLocalConnectionProvider implements ConnectionProvider {

    private final ThreadLocal<Connection> connectionStorage = new ThreadLocal<>();

    @Override
    public void setConnection(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }
        connectionStorage.set(connection);
    }

    @Override
    public Connection getConnection() {
        Connection conn = connectionStorage.get();
        if (conn == null) {
            throw new IllegalStateException("No Connection available in the current thread. Call setConnection() first.");
        }
        return conn;
    }

    @Override
    public boolean hasConnection() {
        return connectionStorage.get() != null;
    }

    @Override
    public void close() {
        Connection conn = connectionStorage.get();
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                System.err.println("Error closing Connection: " + e.getMessage());
            } finally {
                connectionStorage.remove();
            }
        }
    }

    @Override
    public void clear() {
        connectionStorage.remove();
    }
}
