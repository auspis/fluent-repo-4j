package io.github.auspis.repo4j.example;

import io.github.auspis.repo4j.core.provider.ConnectionProvider;
import io.github.auspis.repo4j.core.provider.ConnectionProviderFactory;
import io.github.auspis.repo4j.core.provider.ScopedValueConnectionProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

/**
 * Example demonstrating ScopedValue-based ConnectionProvider.
 * This approach is suitable for virtual threads and structured concurrency.
 */
public class UserExampleWithScopedValue {

    public static void main(String[] args) throws Exception {
        // Step 1: Create a connection
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:example_scoped", "sa", "");

        // Step 2: Create the schema
        createSchema(connection);

        // Step 3: Create ScopedValue provider
        ScopedValueConnectionProvider scopedProvider = 
            (ScopedValueConnectionProvider) ConnectionProviderFactory.scopedValue();

        // Step 4: Execute operations within a scoped context
        scopedProvider.executeInScope(connection, () -> {
            UserRepository userRepository = new UserRepository(scopedProvider);

            System.out.println("=== Using ScopedValue ===");
            System.out.println("\n=== Create ===");
            User user1 = userRepository.create(new User("Alice Scoped", "alice.scoped@example.com"));
            System.out.println("Created: " + user1);

            User user2 = userRepository.create(new User("Bob Scoped", "bob.scoped@example.com"));
            System.out.println("Created: " + user2);

            System.out.println("\n=== FindAll ===");
            List<User> allUsers = userRepository.findAll();
            System.out.println("All users: " + allUsers);

            System.out.println("\n=== Update ===");
            user1.setName("Alice Updated Scoped");
            User updated = userRepository.update(user1);
            System.out.println("Updated: " + updated);

            System.out.println("\n=== Delete ===");
            userRepository.delete(user2.getId());
            System.out.println("Deleted user with ID: " + user2.getId());

            System.out.println("\n=== FindAll after delete ===");
            allUsers = userRepository.findAll();
            System.out.println("Remaining users: " + allUsers);
        });

        // Connection automatically unbound after scope exits
        connection.close();
        System.out.println("\n✅ ScopedValue example completed successfully!");
        System.out.println("Note: Connection was automatically unbound when scope exited.");
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
