package io.github.auspis.fluentrepo4j.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import io.github.auspis.fluentrepo4j.test.domain.User;

/**
 * Integration test demonstrating UserRepository usage with Spring Data Fluent SQL.
 *
 * This test verifies:
 * 1. Auto-configuration of FluentRepositoriesAutoConfiguration
 * 2. Repository scanning and bean creation via FluentRepositoriesRegistrar
 * 3. Proxy creation via FluentRepositoryFactoryBean
 * 4. CRUD operations via SimpleFluentRepository implementation
 * 5. Entity mapping via @Table/@Column/@Id annotations
 * 6. Connection management via FluentConnectionProvider
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Clean up before each test - clear all records
        // Schema was already created by @AutoConfigureTestDatabase which loads schema.sql
        jdbcTemplate.execute("TRUNCATE TABLE \"users\"");
    }

    @Test
    void testRepositoryAutomaticallyRegistered() {
        // Verify Spring Data automatically:
        // 1. Scanned the package and found UserRepository interface
        // 2. Created a bean via FluentRepositoryFactoryBean
        // 3. Wrapped it in a proxy implementing CrudRepository
        // 4. Injected it into this test class

        assertThat(userRepository).isNotNull();
        assertThat(userRepository).isInstanceOf(CrudRepository.class);
    }

    @Test
    void testSave_NewUser() {
        // When: Save a new user (no ID set → INSERT)
        User newUser = new User("John Doe", "john@example.com").withId(1L);
        User saved = userRepository.save(newUser);

        // Then: User is persisted in database
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM \"users\" WHERE \"email\" = ?",
            Integer.class,
            "john@example.com"
        )).isEqualTo(1);
        
        // And: Retrieve from database to get ID
        Long id = jdbcTemplate.queryForObject(
            "SELECT \"id\" FROM \"users\" WHERE \"email\" = ?",
            Long.class,
            "john@example.com"
        );
        assertThat(id).isPositive();
    }

    @Test
    void testFindById() {
        // Given: A user in the database
        User created = new User("Jane Smith", "jane@example.com").withId(1L);
        userRepository.save(created);
        
        // Retrieve the ID from database
        Long userId = jdbcTemplate.queryForObject(
            "SELECT \"id\" FROM \"users\" WHERE \"email\" = ?",
            Long.class,
            "jane@example.com"
        );

        // When: Search by ID
        Optional<User> found = userRepository.findById(userId);

        // Then: User is found with correct data
        assertThat(found)
            .isPresent()
            .hasValueSatisfying(user -> {
                assertThat(user.getName()).isEqualTo("Jane Smith");
                assertThat(user.getEmail()).isEqualTo("jane@example.com");
            });
    }

    @Test
    void testFindById_NotFound() {
        // When: Search for non-existent user
        Optional<User> notFound = userRepository.findById(999L);

        // Then: Optional is empty
        assertThat(notFound).isEmpty();
    }

    @Test
    void testFindAll() {
        // Given: Multiple users in the database
        userRepository.save(new User("Alice", "alice@example.com").withId(1L));
        userRepository.save(new User("Bob", "bob@example.com").withId(2L));
        userRepository.save(new User("Charlie", "charlie@example.com").withId(3L));

        // When: Retrieve all users
        List<User> allUsers = (List<User>) userRepository.findAll();

        // Then: All users are returned
        assertThat(allUsers)
            .hasSize(3)
            .extracting(User::getName)
            .containsExactlyInAnyOrder("Alice", "Bob", "Charlie");
    }

    @Test
    void testSave_Update() {
        // Given: A user in the database
        User original = new User("Original Name", "original@example.com").withId(1L);
        userRepository.save(original);
        
        // Retrieve the ID from database
        Long userId = jdbcTemplate.queryForObject(
            "SELECT \"id\" FROM \"users\" WHERE \"email\" = ?",
            Long.class,
            "original@example.com"
        );
        
        // Set ID for update operations
        original.setId(userId);

        // When: Update the user
        original.setName("Updated Name");
        original.setEmail("updated@example.com");
        User updated = userRepository.save(original);

        // Then: Changes are persisted
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getEmail()).isEqualTo("updated@example.com");

        // And: Retrieved user has updated data
        Optional<User> reloaded = userRepository.findById(userId);
        assertThat(reloaded)
            .isPresent()
            .hasValueSatisfying(user -> {
                assertThat(user.getName()).isEqualTo("Updated Name");
                assertThat(user.getEmail()).isEqualTo("updated@example.com");
            });
    }

    @Test
    void testDeleteById() {
        // Given: A user in the database
        User created = new User("To Delete", "delete@example.com").withId(1L);
        userRepository.save(created);
        
        // Retrieve the ID from database
        Long userId = jdbcTemplate.queryForObject(
            "SELECT \"id\" FROM \"users\" WHERE \"email\" = ?",
            Long.class,
            "delete@example.com"
        );

        // When: Delete the user
        userRepository.deleteById(userId);

        // Then: User is removed from database
        assertThat(userRepository.findById(userId)).isEmpty();
    }

    @Test
    void testCount() {
        // When: Database is empty
        long initialCount = userRepository.count();
        assertThat(initialCount).isZero();

        // Given: Add some users
        userRepository.save(new User("User 1", "user1@example.com").withId(1L));
        userRepository.save(new User("User 2", "user2@example.com").withId(2L));
        userRepository.save(new User("User 3", "user3@example.com").withId(3L));

        // Then: Count is updated
        long finalCount = userRepository.count();
        assertThat(finalCount).isEqualTo(3);
    }

    @Test
    void testSaveAll() {
        // When: Save multiple users at once
        User user1 = new User("User 1", "user1@example.com").withId(1L);
        User user2 = new User("User 2", "user2@example.com").withId(2L);
        User user3 = new User("User 3", "user3@example.com").withId(3L);

        Iterable<User> saved = userRepository.saveAll(List.of(user1, user2, user3));

        // Then: All users are persisted in database
        long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM \"users\" WHERE \"email\" IN (?, ?, ?)",
            Long.class,
            "user1@example.com", "user2@example.com", "user3@example.com"
        );
        assertThat(count).isEqualTo(3);
    }

    @Test
    void testExistsById() {
        // Given: A user in the database
        User created = new User("Existing", "existing@example.com").withId(1L);
        userRepository.save(created);
        
        // Retrieve the ID from database
        Long userId = jdbcTemplate.queryForObject(
            "SELECT \"id\" FROM \"users\" WHERE \"email\" = ?",
            Long.class,
            "existing@example.com"
        );

        // When: Check if user exists
        boolean exists = userRepository.existsById(userId);

        // Then: Existence is confirmed
        assertThat(exists).isTrue();

        // And: Non-existent user returns false
        assertThat(userRepository.existsById(999L)).isFalse();
    }
}
