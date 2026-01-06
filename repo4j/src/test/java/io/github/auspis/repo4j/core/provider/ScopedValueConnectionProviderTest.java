package io.github.auspis.repo4j.core.provider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScopedValueConnectionProvider.
 */
@DisplayName("ScopedValueConnectionProvider Tests")
class ScopedValueConnectionProviderTest {

    private Connection connection;
    private ConnectionProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        long timestamp = System.currentTimeMillis();
        connection = DriverManager.getConnection("jdbc:h2:mem:test_" + timestamp + ";DB_CLOSE_DELAY=-1", "sa", "");
        provider = ConnectionProviderFactory.scopedValue();
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
    @DisplayName("Set and retrieve a Connection (using fallback ThreadLocal)")
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
    @DisplayName("Each factory call creates a new instance with isolated state")
    void testFreshInstancesHaveIsolatedState() {
        // Arrange
        ConnectionProvider provider1 = ConnectionProviderFactory.scopedValue();
        ConnectionProvider provider2 = ConnectionProviderFactory.scopedValue();
        
        // Act
        provider1.setConnection(connection);
        
        // Assert
        assertTrue(provider1.hasConnection());
        assertFalse(provider2.hasConnection());
        assertThrows(IllegalStateException.class, provider2::getConnection);
    }
}
