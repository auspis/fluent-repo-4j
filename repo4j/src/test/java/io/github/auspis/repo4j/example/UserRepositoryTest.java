package io.github.auspis.repo4j.example;

import io.github.auspis.repo4j.core.ConnectionProvider;
import io.github.auspis.repo4j.core.RepositoryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitari per UserRepository con H2 in-memory database.
 */
@DisplayName("UserRepository Tests")
class UserRepositoryTest {

    private UserRepository userRepository;
    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        // Crea H2 in-memory database con DB_CLOSE_DELAY per tenere i dati finché attiva
        long timestamp = System.currentTimeMillis();
        connection = DriverManager.getConnection("jdbc:h2:mem:test_" + timestamp + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        
        // Crea tabella users
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE users (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(255) NOT NULL," +
                    "email VARCHAR(255) UNIQUE NOT NULL" +
                    ")");
        }
        
        // Imposta la Connection nel provider e crea il repository
        ConnectionProvider.setConnection(connection);
        userRepository = new UserRepository();
    }

    void tearDown() throws Exception {
        ConnectionProvider.close();
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Creare un nuovo utente")
    void testCreate() {
        // Arrange
        User newUser = new User("John Doe", "john@example.com");
        
        // Act
        User created = userRepository.create(newUser);
        
        // Assert
        assertNotNull(created.getId());
        assertEquals("John Doe", created.getName());
        assertEquals("john@example.com", created.getEmail());
    }

    @Test
    @DisplayName("Trovare un utente per ID")
    void testFindById() {
        // Arrange
        User newUser = new User("Jane Smith", "jane@example.com");
        User created = userRepository.create(newUser);
        
        // Act
        Optional<User> found = userRepository.findById(created.getId());
        
        // Assert
        assertTrue(found.isPresent());
        assertEquals(created, found.get());
    }

    @Test
    @DisplayName("Trovare un utente per ID inesistente")
    void testFindByIdNotFound() {
        // Act
        Optional<User> found = userRepository.findById(999L);
        
        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Trovare tutti gli utenti")
    void testFindAll() {
        // Arrange
        userRepository.create(new User("User 1", "user1@example.com"));
        userRepository.create(new User("User 2", "user2@example.com"));
        userRepository.create(new User("User 3", "user3@example.com"));
        
        // Act
        List<User> users = userRepository.findAll();
        
        // Assert
        assertEquals(3, users.size());
    }

    @Test
    @DisplayName("Aggiornare un utente")
    void testUpdate() {
        // Arrange
        User original = userRepository.create(new User("Original", "original@example.com"));
        original.setName("Updated");
        original.setEmail("updated@example.com");
        
        // Act
        User updated = userRepository.update(original);
        Optional<User> retrieved = userRepository.findById(updated.getId());
        
        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals("Updated", retrieved.get().getName());
        assertEquals("updated@example.com", retrieved.get().getEmail());
    }

    @Test
    @DisplayName("Cancellare un utente")
    void testDelete() {
        // Arrange
        User user = userRepository.create(new User("To Delete", "delete@example.com"));
        
        // Act
        userRepository.delete(user.getId());
        Optional<User> found = userRepository.findById(user.getId());
        
        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Trovare un utente per email")
    void testFindByEmail() {
        // Arrange
        User created = userRepository.create(new User("Email User", "email@example.com"));
        
        // Act
        Optional<User> found = userRepository.findByEmail("email@example.com");
        
        // Assert
        assertTrue(found.isPresent());
        assertEquals(created, found.get());
    }

    @Test
    @DisplayName("Trovare utenti per nome (pattern matching)")
    void testFindByNameLike() {
        // Arrange
        userRepository.create(new User("Alice", "alice@example.com"));
        userRepository.create(new User("Alicia", "alicia@example.com"));
        userRepository.create(new User("Bob", "bob@example.com"));
        
        // Act
        List<User> results = userRepository.findByNameLike("Ali");
        
        // Assert
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(u -> u.getName().equals("Alice")));
        assertTrue(results.stream().anyMatch(u -> u.getName().equals("Alicia")));
    }

    @Test
    @DisplayName("Eccezione quando nessuna Connection è disponibile")
    void testNoConnectionAvailable() {
        // Arrange
        ConnectionProvider.clear();
        UserRepository repo = new UserRepository();
        
        // Act & Assert
        assertThrows(IllegalStateException.class, repo::findAll);
    }

    @Test
    @DisplayName("Operazioni multiple nella stessa transazione")
    void testMultipleOperations() {
        // Arrange & Act
        User user1 = userRepository.create(new User("User One", "one@example.com"));
        User user2 = userRepository.create(new User("User Two", "two@example.com"));
        user1.setName("Updated One");
        userRepository.update(user1);
        
        // Assert
        List<User> all = userRepository.findAll();
        assertEquals(2, all.size());
        Optional<User> updated = userRepository.findById(user1.getId());
        assertEquals("Updated One", updated.get().getName());
    }
}
