package io.github.auspis.repo4j.spike.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import io.github.auspis.repo4j.spike.core.provider.ConnectionProvider;
import io.github.auspis.repo4j.spike.core.provider.ConnectionProviderFactory;

/**
 * Example usage of the Repository Pattern with pure JDBC.
 * Demonstrates the Connection lifecycle and CRUD method usage.
 */
public class UserExample {

    public static void main(String[] args) throws Exception {
        // Step 1: Create a connection (usually from DataSource/Connection Pool)
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:example", "sa", "");

        // Step 2: Create the schema (usually done once)
        createSchema(connection);

        // Step 3: Set the connection in the provider (ThreadLocal)
        ConnectionProvider connectionProvider = ConnectionProviderFactory.threadLocal();
        connectionProvider.setConnection(connection);

        try {
            // Step 4: Use the repository - Zero Connection in method signatures!
            UserRepository userRepository = new UserRepository(connectionProvider);

            System.out.println("=== Create ===");
            User user1 = userRepository.create(new User("Alice", "alice@example.com"));
            System.out.println("Created: " + user1);

            User user2 = userRepository.create(new User("Bob", "bob@example.com"));
            System.out.println("Created: " + user2);

            System.out.println("\n=== FindById ===");
            Optional<User> found = userRepository.findById(user1.getId());
            System.out.println("Found: " + found);

            System.out.println("\n=== FindAll ===");
            List<User> allUsers = userRepository.findAll();
            System.out.println("All users: " + allUsers);

            System.out.println("\n=== Update ===");
            user1.setName("Alice Updated");
            user1.setEmail("alice.updated@example.com");
            User updated = userRepository.update(user1);
            System.out.println("Updated: " + updated);

            System.out.println("\n=== FindByEmail (custom query) ===");
            Optional<User> foundByEmail = userRepository.findByEmail("bob@example.com");
            System.out.println("Found by email: " + foundByEmail);

            System.out.println("\n=== FindByNameLike (custom query) ===");
            userRepository.create(new User("Alicia", "alicia@example.com"));
            List<User> likeAli = userRepository.findByNameLike("Ali");
            System.out.println("Users containing 'Ali': " + likeAli);

            System.out.println("\n=== Delete ===");
            userRepository.delete(user2.getId());
            System.out.println("Deleted user with ID: " + user2.getId());

            System.out.println("\n=== FindAll after delete ===");
            allUsers = userRepository.findAll();
            System.out.println("Remaining users: " + allUsers);

        } finally {
            // Step 5: Clean up the connection
            connectionProvider.close();
        }

        System.out.println("\n✅ Example completed successfully!");
    }

    private static void createSchema(Connection connection) throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE users (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(255) NOT NULL," +
                    "email VARCHAR(255) UNIQUE NOT NULL" +
                    ")");
        }
    }
}
