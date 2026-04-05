package io.github.auspis.fluentrepo4j.example;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.functional.RepositoryResult;
import io.github.auspis.fluentrepo4j.functional.RepositoryResult.Success;
import io.github.auspis.fluentrepo4j.repository.FunctionalFluentRepository;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest;
import io.github.auspis.fluentsql4j.test.util.database.TestDatabaseUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for functional repository operations.
 * Verifies that {@link FunctionalFluentRepository} correctly wraps outcomes
 * in {@link RepositoryResult}.
 */
@IntegrationTest
@SpringBootTest
@ActiveProfiles("test")
class FunctionalUserRepositoryIntegrationTest {

    @Autowired
    private FunctionalUserRepository repository;

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
    void repositoryIsAutomaticallyRegistered() {
        assertThat(repository).isNotNull();
    }

    @Nested
    class SaveScenarios {

        @Test
        void saveReturnsSuccess() {
            User user = new User("Functional User", "func@example.com").withId(20L);
            RepositoryResult<User> result = repository.save(user);

            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow().getName()).isEqualTo("Functional User");
        }

        @Test
        void saveAllReturnsSuccess() {
            List<User> users = List.of(
                    new User("Batch1", "batch1@test.com").withId(30L),
                    new User("Batch2", "batch2@test.com").withId(31L));

            RepositoryResult<List<User>> result = repository.saveAll(users);
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).hasSize(2);
        }
    }

    @Nested
    class FindScenarios {

        @Test
        void findByIdReturnsSuccessWithValue() {
            RepositoryResult<Optional<User>> result = repository.findById(1L);
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isPresent().hasValueSatisfying(user -> {
                assertThat(user.getName()).isEqualTo("John Doe");
            });
        }

        @Test
        void findByIdReturnsSuccessWithEmptyOptional() {
            RepositoryResult<Optional<User>> result = repository.findById(999L);
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isEmpty();
        }

        @Test
        void findAllReturnsSuccess() {
            RepositoryResult<List<User>> result = repository.findAll();
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).hasSize(10);
        }

        @Test
        void findAllByIdReturnsSuccess() {
            RepositoryResult<List<User>> result = repository.findAllById(List.of(1L, 2L, 999L));
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).hasSize(2);
        }

        @Test
        void existsByIdReturnsTrue() {
            RepositoryResult<Boolean> result = repository.existsById(1L);
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isTrue();
        }

        @Test
        void existsByIdReturnsFalse() {
            RepositoryResult<Boolean> result = repository.existsById(999L);
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isFalse();
        }

        @Test
        void countReturnsSuccess() {
            RepositoryResult<Long> result = repository.count();
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isEqualTo(10L);
        }
    }

    @Nested
    class DeleteScenarios {

        @Test
        void deleteByIdReturnsTrueWhenExists() {
            RepositoryResult<Boolean> result = repository.deleteById(1L);
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isTrue();

            assertThat(repository.findById(1L).orElseThrow()).isEmpty();
        }

        @Test
        void deleteByIdReturnsFalseWhenNotFound() {
            RepositoryResult<Boolean> result = repository.deleteById(999L);
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isFalse();
        }

        @Test
        void deleteReturnsSuccess() {
            User user = repository.findById(1L).orElseThrow().orElseThrow();
            RepositoryResult<Boolean> result = repository.delete(user);
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isTrue();
        }

        @Test
        void deleteAllByIdReturnsCount() {
            RepositoryResult<Long> result = repository.deleteAllById(List.of(1L, 2L, 999L));
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isEqualTo(2L);
        }

        @Test
        void deleteAllReturnsCount() {
            RepositoryResult<Long> result = repository.deleteAll();
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isEqualTo(10L);
        }
    }

    @Nested
    class PagingAndSortingScenarios {

        @Test
        void findAllSortedReturnsSuccess() {
            RepositoryResult<List<User>> result = repository.findAll(Sort.by("name"));
            assertThat(result).isInstanceOf(Success.class);
            List<User> users = result.orElseThrow();
            assertThat(users).hasSize(10);
            assertThat(users.get(0).getName()).isLessThanOrEqualTo(users.get(1).getName());
        }

        @Test
        void findAllPagedReturnsSuccess() {
            RepositoryResult<Page<User>> result = repository.findAll(PageRequest.of(0, 3, Sort.by("name")));
            assertThat(result).isInstanceOf(Success.class);
            Page<User> page = result.orElseThrow();
            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getTotalElements()).isEqualTo(10);
            assertThat(page.getTotalPages()).isEqualTo(4);
        }
    }

    @Nested
    class DerivedQueryScenarios {

        @Test
        void findByEmailReturnsSuccess() {
            RepositoryResult<Optional<User>> result = repository.findByEmail("john@example.com");
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isPresent().hasValueSatisfying(user -> assertThat(user.getName())
                    .isEqualTo("John Doe"));
        }

        @Test
        void findByEmailReturnsEmptyOptional() {
            RepositoryResult<Optional<User>> result = repository.findByEmail("nonexistent@example.com");
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isEmpty();
        }

        @Test
        void findByNameReturnsSuccess() {
            RepositoryResult<List<User>> result = repository.findByName("John Doe");
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isNotEmpty();
        }

        @Test
        void countByActiveReturnsSuccess() {
            RepositoryResult<Long> result = repository.countByActive(true);
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isGreaterThan(0L);
        }

        @Test
        void existsByEmailReturnsSuccess() {
            RepositoryResult<Boolean> result = repository.existsByEmail("john@example.com");
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isTrue();
        }

        @Test
        void existsByEmailReturnsFalse() {
            RepositoryResult<Boolean> result = repository.existsByEmail("nobody@example.com");
            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isFalse();
        }
    }
}
