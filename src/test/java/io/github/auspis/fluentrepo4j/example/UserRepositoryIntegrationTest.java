package io.github.auspis.fluentrepo4j.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest;
import io.github.auspis.fluentsql4j.test.util.database.TestDatabaseUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test demonstrating UserRepository usage with Spring Data Fluent SQL.
 *
 * <p>This test verifies:
 *
 * <ol>
 *   <li>Auto-configuration of FluentRepositoriesAutoConfiguration
 *   <li>Repository scanning and bean creation via FluentRepositoriesRegistrar
 *   <li>Proxy creation via FluentRepositoryFactoryBean
 *   <li>CRUD operations via FluentRepository implementation
 *   <li>Entity mapping via @Table/@Column/@Id annotations
 *   <li>Connection management via FluentConnectionProvider
 * </ol>
 */
@IntegrationTest
@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            TestDatabaseUtil.H2.truncateUsers(connection);
            TestDatabaseUtil.H2.insertSampleUsers(connection);
        }
    }

    @Test
    void repositoryAutomaticallyRegistered() {
        assertThat(userRepository)
                .isNotNull()
                .isInstanceOf(CrudRepository.class)
                .isInstanceOf(PagingAndSortingRepository.class);
    }

    @Nested
    class CrudScenarios {

        @Test
        void save_insert() {
            long countBefore = userRepository.count();
            userRepository.save(new User("New User", "newuser@example.com").withId(11L));
            assertThat(userRepository.count()).isEqualTo(countBefore + 1);
        }

        @Test
        void findById() {
            Optional<User> found = userRepository.findById(1L);
            assertThat(found).isPresent().hasValueSatisfying(user -> {
                assertThat(user.getName()).isEqualTo("John Doe");
                assertThat(user.getEmail()).isEqualTo("john@example.com");
            });
        }

        @Test
        void findById_notFound() {
            assertThat(userRepository.findById(999L)).isEmpty();
        }

        @Test
        void findAll() {
            Iterable<User> allUsers = userRepository.findAll();
            assertThat(allUsers).hasSize(10);
        }

        @Test
        void save_update() {
            User user = userRepository.findById(1L).orElseThrow();
            user.setName("Updated John");
            user.setEmail("updated-john@example.com");
            User updated = userRepository.save(user);

            assertThat(updated.getName()).isEqualTo("Updated John");
            assertThat(updated.getEmail()).isEqualTo("updated-john@example.com");

            assertThat(userRepository.findById(1L)).isPresent().hasValueSatisfying(reloaded -> {
                assertThat(reloaded.getName()).isEqualTo("Updated John");
                assertThat(reloaded.getEmail()).isEqualTo("updated-john@example.com");
            });
        }

        @Test
        void deleteById() {
            userRepository.deleteById(1L);
            assertThat(userRepository.findById(1L)).isEmpty();
        }

        @Test
        void count() {
            assertThat(userRepository.count()).isEqualTo(10);
            userRepository.save(new User("Extra User", "extra@example.com").withId(11L));
            assertThat(userRepository.count()).isEqualTo(11);
        }

        @Test
        void saveAll() {
            long countBefore = userRepository.count();
            userRepository.saveAll(List.of(
                    new User("Extra 1", "extra1@example.com").withId(11L),
                    new User("Extra 2", "extra2@example.com").withId(12L),
                    new User("Extra 3", "extra3@example.com").withId(13L)));
            assertThat(userRepository.count()).isEqualTo(countBefore + 3);
        }

        @Test
        void existsById() {
            assertThat(userRepository.existsById(1L)).isTrue();
            assertThat(userRepository.existsById(999L)).isFalse();
        }
    }

    @Nested
    class SortingAndPagingScenarios {

        @Test
        void findAll_sortedByName() {
            Iterable<User> users = userRepository.findAll(Sort.by("name"));
            assertThat(users)
                    .extracting(User::getName)
                    .containsExactly(
                            "Alice",
                            "Bob",
                            "Charlie",
                            "Diana",
                            "Eve",
                            "Frank",
                            "Grace",
                            "Henry",
                            "Jane Smith",
                            "John Doe");
        }

        @Test
        void findAll_sortedByAgeDesc() {
            Iterable<User> users = userRepository.findAll(Sort.by(Sort.Direction.DESC, "age"));
            assertThat(users).extracting(User::getAge).isSortedAccordingTo(Comparator.reverseOrder());
        }

        @Test
        void findAll_sortedById() {
            Iterable<User> users = userRepository.findAll(Sort.by("id"));
            assertThat(users).extracting(User::getId).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        }

        @Test
        void findAll_sortedByMultipleFields() {
            Iterable<User> users = userRepository.findAll(Sort.by(Sort.Order.asc("age"), Sort.Order.asc("name")));
            // age asc, then name asc for ties:
            // 15: Bob | 25: Diana, Jane Smith | 28: Grace | 30: Charlie, Henry, John Doe | 35: Alice, Frank | 40: Eve
            assertThat(users)
                    .extracting(User::getName)
                    .containsExactly(
                            "Bob",
                            "Diana",
                            "Jane Smith",
                            "Grace",
                            "Charlie",
                            "Henry",
                            "John Doe",
                            "Alice",
                            "Frank",
                            "Eve");
        }

        @Test
        void findAllSorted_unknownProperty_throwsException() {
            Sort sortBy = Sort.by("nonExistent");
            assertThatThrownBy(() -> userRepository.findAll(sortBy))
                    .isInstanceOf(InvalidDataAccessApiUsageException.class)
                    .hasMessageContaining("nonExistent");
        }

        @Test
        void findAllPaged_firstPage() {
            Page<User> page = userRepository.findAll(PageRequest.of(0, 3));
            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getTotalElements()).isEqualTo(10);
            assertThat(page.getTotalPages()).isEqualTo(4);
            assertThat(page.getNumber()).isZero();
            assertThat(page.isFirst()).isTrue();
            assertThat(page.hasNext()).isTrue();
        }

        @Test
        void findAllPaged_secondPage() {
            Page<User> page = userRepository.findAll(PageRequest.of(1, 3));
            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getTotalElements()).isEqualTo(10);
            assertThat(page.getNumber()).isEqualTo(1);
        }

        @Test
        void findAllPaged_lastPagePartial() {
            Page<User> page = userRepository.findAll(PageRequest.of(3, 3));
            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(10);
            assertThat(page.isLast()).isTrue();
            assertThat(page.hasNext()).isFalse();
        }

        @Test
        void findAllPaged_beyondLastPage() {
            Page<User> page = userRepository.findAll(PageRequest.of(10, 3));
            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(10);
            assertThat(page.getNumber()).isEqualTo(10);
        }

        @Test
        void findAllPaged_withSort() {
            Page<User> page = userRepository.findAll(PageRequest.of(0, 5, Sort.by("name")));
            assertThat(page.getContent()).hasSize(5);
            assertThat(page.getContent())
                    .extracting(User::getName)
                    .containsExactly("Alice", "Bob", "Charlie", "Diana", "Eve");
            assertThat(page.getTotalElements()).isEqualTo(10);
        }

        @Test
        void findAllPaged_withSortTieAndTieBreaker() {
            // All rows sorted by (age ASC, id ASC):
            // Bob(15,3), JaneSmith(25,2), Diana(25,6), Grace(28,9),
            // JohnDoe(30,1), Charlie(30,5), Henry(30,10), Alice(35,4), Frank(35,8), Eve(40,7)
            // Page 1 size 4 → indices 4-7: John Doe, Charlie, Henry, Alice
            Page<User> page =
                    userRepository.findAll(PageRequest.of(1, 4, Sort.by(Sort.Order.asc("age"), Sort.Order.asc("id"))));
            assertThat(page.getContent()).hasSize(4);
            assertThat(page.getContent())
                    .extracting(User::getName)
                    .containsExactly("John Doe", "Charlie", "Henry", "Alice");
            assertThat(page.getTotalElements()).isEqualTo(10);
        }

        @Test
        void findAllPaged_emptyTable() throws SQLException {
            try (Connection connection = dataSource.getConnection()) {
                TestDatabaseUtil.H2.truncateUsers(connection);
            }
            Page<User> page = userRepository.findAll(PageRequest.of(0, 10));
            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getTotalPages()).isZero();
        }
    }
}
