package io.github.auspis.fluentrepo4j.example;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.test.util.QueryUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.ActiveProfiles;

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

    private static final String USERS_TABLE = "\"users\"";
    private static final String ID_COLUMN = "\"id\"";
    private static final String EMAIL_COLUMN = "\"email\"";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DSL dsl;

    @BeforeEach
    void setUp() throws SQLException {
        // Clean up before each test - clear all records.
        // Schema was already created by @AutoConfigureTestDatabase which loads schema.sql.
        try (Connection connection = dataSource.getConnection();
                var ps = dsl.truncateTable("users").build(connection)) {
            ps.executeUpdate();
        }
    }

    @Test
    void repositoryAutomaticallyRegistered() {
        // Verify Spring Data automatically:
        // 1. Scanned the package and found UserRepository interface
        // 2. Created a bean via FluentRepositoryFactoryBean
        // 3. Wrapped it in a proxy implementing CrudRepository
        // 4. Injected it into this test class

        assertThat(userRepository).isNotNull().isInstanceOf(CrudRepository.class);
    }

    @Test
    void save_insert() {
        User newUser = new User("John Doe", "john@example.com").withId(1L);
        userRepository.save(newUser);

        assertThat(countUsersByEmail("john@example.com")).isEqualTo(1);

        Long id = findUserIdByEmail("john@example.com").orElseThrow();
        assertThat(id).isPositive();
    }

    @Test
    void findById() {
        User created = new User("Jane Smith", "jane@example.com").withId(1L);
        userRepository.save(created);

        Long userId = findUserIdByEmail("jane@example.com").orElseThrow();

        Optional<User> found = userRepository.findById(userId);

        assertThat(found).isPresent().hasValueSatisfying(user -> {
            assertThat(user.getName()).isEqualTo("Jane Smith");
            assertThat(user.getEmail()).isEqualTo("jane@example.com");
        });
    }

    @Test
    void findById_NotFound() {
        Optional<User> notFound = userRepository.findById(999L);

        assertThat(notFound).isEmpty();
    }

    @Test
    void findAll() {
        userRepository.save(new User("Alice", "alice@example.com").withId(1L));
        userRepository.save(new User("Bob", "bob@example.com").withId(2L));
        userRepository.save(new User("Charlie", "charlie@example.com").withId(3L));

        List<User> allUsers = (List<User>) userRepository.findAll();

        assertThat(allUsers).hasSize(3).extracting(User::getName).containsExactlyInAnyOrder("Alice", "Bob", "Charlie");
    }

    @Test
    void save_update() {
        User original = new User("Original Name", "original@example.com").withId(1L);
        userRepository.save(original);

        Long userId = findUserIdByEmail("original@example.com").orElseThrow();

        original.setId(userId);
        original.setName("Updated Name");
        original.setEmail("updated@example.com");
        User updated = userRepository.save(original);

        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getEmail()).isEqualTo("updated@example.com");

        Optional<User> reloaded = userRepository.findById(userId);
        assertThat(reloaded).isPresent().hasValueSatisfying(user -> {
            assertThat(user.getName()).isEqualTo("Updated Name");
            assertThat(user.getEmail()).isEqualTo("updated@example.com");
        });
    }

    @Test
    void deleteById() {
        User created = new User("To Delete", "delete@example.com").withId(1L);
        userRepository.save(created);

        Long userId = findUserIdByEmail("delete@example.com").orElseThrow();

        userRepository.deleteById(userId);

        assertThat(userRepository.findById(userId)).isEmpty();
    }

    @Test
    void count() {
        long initialCount = userRepository.count();
        assertThat(initialCount).isZero();

        userRepository.save(new User("User 1", "user1@example.com").withId(1L));
        userRepository.save(new User("User 2", "user2@example.com").withId(2L));
        userRepository.save(new User("User 3", "user3@example.com").withId(3L));

        long finalCount = userRepository.count();
        assertThat(finalCount).isEqualTo(3);
    }

    @Test
    void saveAll() {
        User user1 = new User("User 1", "user1@example.com").withId(1L);
        User user2 = new User("User 2", "user2@example.com").withId(2L);
        User user3 = new User("User 3", "user3@example.com").withId(3L);

        userRepository.saveAll(List.of(user1, user2, user3));

        long count = countUsersByEmails("user1@example.com", "user2@example.com", "user3@example.com");
        assertThat(count).isEqualTo(3);
    }

    @Test
    void existsById() {
        User created = new User("Existing", "existing@example.com").withId(1L);
        userRepository.save(created);

        Long userId = findUserIdByEmail("existing@example.com").orElseThrow();

        boolean exists = userRepository.existsById(userId);

        assertThat(exists).isTrue();
        assertThat(userRepository.existsById(999L)).isFalse();
    }

    private long countUsersByEmail(String email) {
        try (Connection connection = dataSource.getConnection()) {
            return QueryUtil.countByColumn(connection, USERS_TABLE, EMAIL_COLUMN, email);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private long countUsersByEmails(String... emails) {
        try (Connection connection = dataSource.getConnection()) {
            return QueryUtil.countByColumnIn(connection, USERS_TABLE, EMAIL_COLUMN, (Object[]) emails);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Long> findUserIdByEmail(String email) {
        try (Connection connection = dataSource.getConnection()) {
            return QueryUtil.getSingleValueByColumn(
                    connection, USERS_TABLE, ID_COLUMN, EMAIL_COLUMN, email, Long.class);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
