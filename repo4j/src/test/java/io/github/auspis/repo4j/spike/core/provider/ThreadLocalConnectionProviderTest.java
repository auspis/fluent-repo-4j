package io.github.auspis.repo4j.spike.core.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ThreadLocalConnectionProvider.
 */
@DisplayName("ThreadLocalConnectionProvider Tests")
class ThreadLocalConnectionProviderTest {

    private Connection connection;
    private ConnectionProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        long timestamp = System.currentTimeMillis();
        connection = DriverManager.getConnection("jdbc:h2:mem:test_" + timestamp + ";DB_CLOSE_DELAY=-1", "sa", "");
        provider = ConnectionProviderFactory.threadLocal();
    }

    @AfterEach
    void tearDown() {
        provider.clear();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    @DisplayName("Set and retrieve a Connection")
    void testSetAndGetConnection() {
        // Act
        provider.setConnection(connection);
        Connection retrieved = provider.getConnection();
        
        // Assert
        assertSame(connection, retrieved);
    }

    @Test
    @DisplayName("Throw exception if Connection is not set")
    void testGetConnectionThrowsWhenNotSet() {
        // Act & Assert
        assertThrows(IllegalStateException.class, provider::getConnection);
    }

    @Test
    @DisplayName("Throw exception if setConnection receives null")
    void testSetConnectionThrowsOnNull() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> provider.setConnection(null));
    }

    @Test
    @DisplayName("Check Connection availability")
    void testHasConnection() {
        // Arrange
        assertFalse(provider.hasConnection());
        
        // Act
        provider.setConnection(connection);
        
        // Assert
        assertTrue(provider.hasConnection());
    }

    @Test
    @DisplayName("Close Connection")
    void testClose() throws Exception {
        // Arrange
        provider.setConnection(connection);
        
        // Act
        provider.close();
        
        // Assert
        assertFalse(provider.hasConnection());
        assertTrue(connection.isClosed());
    }

    @Test
    @DisplayName("Clear Connection without closing it")
    void testClear() throws Exception {
        // Arrange
        provider.setConnection(connection);
        
        // Act
        provider.clear();
        
        // Assert
        assertFalse(provider.hasConnection());
        assertFalse(connection.isClosed());
    }

    @Test
    @DisplayName("ThreadLocal isolation between threads")
    void testThreadLocalIsolation() throws InterruptedException {
        // Arrange
        provider.setConnection(connection);
        Exception[] exception = new Exception[1];
        
        Thread otherThread = new Thread(() -> {
            try {
                // In the new thread, there should be no Connection
                assertFalse(provider.hasConnection());
                assertThrows(IllegalStateException.class, provider::getConnection);
            } catch (Exception e) {
                exception[0] = e;
            }
        });
        
        // Act
        otherThread.start();
        otherThread.join();
        
        // Assert
        assertNull(exception[0]);
        // In the main thread, the Connection is still available
        assertTrue(provider.hasConnection());
    }

    @Test
    @DisplayName("Each factory call creates a new instance with isolated state")
    void testFreshInstancesHaveIsolatedState() {
        // Arrange
        ConnectionProvider provider1 = ConnectionProviderFactory.threadLocal();
        ConnectionProvider provider2 = ConnectionProviderFactory.threadLocal();
        
        // Act
        provider1.setConnection(connection);
        
        // Assert
        assertTrue(provider1.hasConnection());
        assertFalse(provider2.hasConnection());
        assertThrows(IllegalStateException.class, provider2::getConnection);
    }
}
