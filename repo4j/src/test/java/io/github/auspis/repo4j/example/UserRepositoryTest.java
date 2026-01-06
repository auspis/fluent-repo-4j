package io.github.auspis.repo4j.example;

import io.github.auspis.repo4j.core.provider.ConnectionProvider;
import io.github.auspis.repo4j.core.provider.ConnectionProviderFactory;
import org.junit.jupiter.api.AfterEach;
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
 * Unit tests for UserRepository with H2 in-memory database.
 */
@DisplayName("UserRepository Tests")
class UserRepositoryTest {

    private UserRepository userRepository;
    private Connection connection;
    private ConnectionProvider connectionProvider;

    @BeforeEach
    void setUp() throws Exception {
        // Create H2 in-memory database with DB_CLOSE_DELAY to keep data while active
        long timestamp = System.currentTimeMillis();
        connection = DriverManager.getConnection("jdbc:h2:mem:test_" + timestamp + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        
        // Create users table
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE users (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(255) NOT NULL," +
                    "email VARCHAR(255) UNIQUE NOT NULL" +
                    ")");
        }
        
        // Set the Connection in the provider and create the repository
        connectionProvider = ConnectionProviderFactory.threadLocal();
        connectionProvider.setConnection(connection);
        userRepository = new UserRepository(connectionProvider);
    }

    @AfterEach
    void tearDown() throws Exception {
        connectionProvider.close();
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Create a new user")
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
    @DisplayName("Find a user by ID")
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
    @DisplayName("Find a user by non-existent ID")
    void testFindByIdNotFound() {
        // Act
        Optional<User> found = userRepository.findById(999L);
        
        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Find all users")
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
    @DisplayName("Update a user")
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
    @DisplayName("Delete a user")
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
    @DisplayName("Find a user by email")
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
    @DisplayName("Find users by name (pattern matching)")
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
    @DisplayName("Exception when no Connection is available")
    void testNoConnectionAvailable() {
        // Arrange
        connectionProvider.clear();
        
        // Act & Assert
        assertThrows(IllegalStateException.class, userRepository::findAll);
    }

    @Test
    @DisplayName("Multiple operations in the same context")
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
