package io.github.auspis.repo4j.core.provider;

/**
 * Factory for creating ConnectionProvider instances.
 * Provides static methods to instantiate different implementations.
 * Each call creates a fresh instance with no shared state.
 */
public final class ConnectionProviderFactory {

    private ConnectionProviderFactory() {
        // Utility class, not instantiable
    }

    /**
     * Creates a new ThreadLocal-based ConnectionProvider instance.
     * Suitable for traditional thread pool scenarios.
     * Each instance has its own ThreadLocal storage.
     *
     * @return a new ThreadLocalConnectionProvider instance
     */
    public static ConnectionProvider threadLocal() {
        return new ThreadLocalConnectionProvider();
    }

    /**
     * Creates a new ScopedValue-based ConnectionProvider instance.
     * Suitable for virtual threads and structured concurrency scenarios.
     * Each instance has its own ScopedValue storage.
     *
     * @return a new ScopedValueConnectionProvider instance
     */
    public static ConnectionProvider scopedValue() {
        return new ScopedValueConnectionProvider();
    }
}
