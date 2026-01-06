package io.github.auspis.repo4j.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitari per ConnectionProvider.
 */
@DisplayName("ConnectionProvider Tests")
class ConnectionProviderTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        long timestamp = System.currentTimeMillis();
        connection = DriverManager.getConnection("jdbc:h2:mem:test_" + timestamp + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    @AfterEach
    void tearDown() {
        ConnectionProvider.clear();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    @DisplayName("Impostare e recuperare una Connection")
    void testSetAndGetConnection() {
        // Act
        ConnectionProvider.setConnection(connection);
        Connection retrieved = ConnectionProvider.getConnection();
        
        // Assert
        assertSame(connection, retrieved);
    }

    @Test
    @DisplayName("Lancio eccezione se Connection non è impostata")
    void testGetConnectionThrowsWhenNotSet() {
        // Act & Assert
        assertThrows(IllegalStateException.class, ConnectionProvider::getConnection);
    }

    @Test
    @DisplayName("Lancio eccezione se setConnection riceve null")
    void testSetConnectionThrowsOnNull() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> ConnectionProvider.setConnection(null));
    }

    @Test
    @DisplayName("Verifica disponibilità Connection")
    void testHasConnection() {
        // Arrange
        assertFalse(ConnectionProvider.hasConnection());
        
        // Act
        ConnectionProvider.setConnection(connection);
        
        // Assert
        assertTrue(ConnectionProvider.hasConnection());
    }

    @Test
    @DisplayName("Chiudere Connection")
    void testClose() throws Exception {
        // Arrange
        ConnectionProvider.setConnection(connection);
        
        // Act
        ConnectionProvider.close();
        
        // Assert
        assertFalse(ConnectionProvider.hasConnection());
        assertTrue(connection.isClosed());
    }

    @Test
    @DisplayName("Pulire Connection senza chiuderla")
    void testClear() throws Exception {
        // Arrange
        ConnectionProvider.setConnection(connection);
        
        // Act
        ConnectionProvider.clear();
        
        // Assert
        assertFalse(ConnectionProvider.hasConnection());
        assertFalse(connection.isClosed());
    }

    @Test
    @DisplayName("ThreadLocal isolation tra thread")
    void testThreadLocalIsolation() throws InterruptedException {
        // Arrange
        ConnectionProvider.setConnection(connection);
        Connection[] otherThreadConnection = new Connection[1];
        Exception[] exception = new Exception[1];
        
        Thread otherThread = new Thread(() -> {
            try {
                // Nel nuovo thread, non dovrebbe esserci nessuna Connection
                assertFalse(ConnectionProvider.hasConnection());
                assertThrows(IllegalStateException.class, ConnectionProvider::getConnection);
            } catch (Exception e) {
                exception[0] = e;
            }
        });
        
        // Act
        otherThread.start();
        otherThread.join();
        
        // Assert
        assertNull(exception[0]);
        // Nel thread principale, la Connection è ancora disponibile
        assertTrue(ConnectionProvider.hasConnection());
    }
}
