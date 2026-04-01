package io.github.auspis.fluentrepo4j.query.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.dialect.DialectDetector;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.query.runtime.FluentRepositoryQuery;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest;
import io.github.auspis.fluentsql4j.test.util.database.DataUtil.UserRecord;
import io.github.auspis.fluentsql4j.test.util.database.TestDatabaseUtil;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Integration tests for dynamic query methods using an in-memory H2 database.
 * Verifies the full execution path:
 * PartTreeAdapter → QueryDescriptor → QueryDescriptorToDslMapper → FluentRepositoryQuery.
 */
@IntegrationTest
class DynamicQueryIT {

    // ---- Sample repository interface ----

    @SuppressWarnings("unused")
    interface UserDynamicRepository extends org.springframework.data.repository.CrudRepository<User, Long> {

        List<User> findByName(String name);

        Optional<User> findFirstByName(String name);

        List<User> findByNameAndEmail(String name, String email);

        List<User> findByNameOrEmail(String name, String email);

        List<User> findByAgeLessThan(Integer age);

        List<User> findByAgeGreaterThan(Integer age);

        List<User> findByAgeBetween(Integer min, Integer max);

        List<User> findByNameIsNull();

        List<User> findByNameIsNotNull();

        List<User> findByNameContaining(String fragment);

        List<User> findByNameStartingWith(String prefix);

        List<User> findByNameEndingWith(String suffix);

        List<User> findByNameIgnoreCase(String name);

        List<User> findByNameContainingIgnoreCase(String fragment);

        List<User> findByNameIn(Collection<String> names);

        List<User> findByActiveTrue();

        List<User> findByActiveFalse();

        long countByName(String name);

        boolean existsByEmail(String email);

        void deleteByName(String name);

        List<User> findByName(String name, Sort sort);

        Page<User> findByActive(Boolean active, Pageable pageable);

        List<User> findTop2ByAgeGreaterThan(Integer age);

        Stream<User> findByEmail(String email);

        Slice<User> findByAgeGreaterThan(Integer age, Pageable pageable);

        long deleteByEmail(String email);

        int deleteByActive(Boolean active);
    }

    // ---- Test infrastructure ----

    private Connection connection;
    private DSL dsl;
    private FluentConnectionProvider connectionProvider;
    private FluentEntityInformation<User, Long> entityInfo;
    private RepositoryMetadata metadata;

    @BeforeEach
    void setUp() throws SQLException {
        connection = TestDatabaseUtil.H2.createConnection();
        TestDatabaseUtil.H2.createUsersTable(connection);

        // Insert test data
        TestDatabaseUtil.H2.insertUser(
                connection,
                new UserRecord(1L, "Alice", "alice@example.com", 30, true, "1994-05-01", "2023-01-01", "{}", "{}"));
        TestDatabaseUtil.H2.insertUser(
                connection,
                new UserRecord(2L, "Bob", "bob@example.com", 25, true, "1999-06-15", "2023-02-01", "{}", "{}"));
        TestDatabaseUtil.H2.insertUser(
                connection,
                new UserRecord(
                        3L, "Charlie", "charlie@example.com", 40, false, "1984-03-20", "2023-03-01", "{}", "{}"));
        TestDatabaseUtil.H2.insertUser(
                connection,
                new UserRecord(4L, "Alice", "alice2@example.com", 35, false, "1989-07-10", "2023-04-01", "{}", "{}"));

        DataSource dataSource = new SingleConnectionDataSource(connection, true);
        DSLRegistry registry = DSLRegistry.createWithServiceLoader();
        dsl = DialectDetector.detect(dataSource, registry);
        connectionProvider = new FluentConnectionProvider(dataSource);
        entityInfo = new FluentEntityInformation<>(User.class);
        metadata = new DefaultRepositoryMetadata(UserDynamicRepository.class);
    }

    @AfterEach
    void tearDown() throws SQLException {
        TestDatabaseUtil.H2.closeConnection(connection);
    }

    private Object executeQuery(String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method m = UserDynamicRepository.class.getMethod(methodName, paramTypes);
        FluentRepositoryQuery<User, Long> query = new FluentRepositoryQuery<>(
                m, metadata, new SpelAwareProxyProjectionFactory(), entityInfo, connectionProvider, dsl);
        return query.execute(args);
    }

    // ============================================================
    // FIND BY SINGLE PROPERTY
    // ============================================================

    @Nested
    class FindBySingleProperty {

        @Test
        @SuppressWarnings("unchecked")
        void findByName_returns_matching_users() throws Exception {
            List<User> result =
                    (List<User>) executeQuery("findByName", new Class[] {String.class}, new Object[] {"Alice"});
            assertThat(result).hasSize(2).allMatch(u -> "Alice".equals(u.getName()));
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByName_no_results_for_unknown() throws Exception {
            List<User> result =
                    (List<User>) executeQuery("findByName", new Class[] {String.class}, new Object[] {"Unknown"});
            assertThat(result).isEmpty();
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByActiveTrue_returns_active_users() throws Exception {
            List<User> result = (List<User>) executeQuery("findByActiveTrue", new Class[] {}, new Object[] {});
            assertThat(result).hasSize(2).allMatch(u -> Boolean.TRUE.equals(u.getActive()));
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByActiveFalse_returns_inactive_users() throws Exception {
            List<User> result = (List<User>) executeQuery("findByActiveFalse", new Class[] {}, new Object[] {});
            assertThat(result).hasSize(2).allMatch(u -> Boolean.FALSE.equals(u.getActive()));
        }
    }

    // ============================================================
    // LOGICAL COMBINATIONS
    // ============================================================

    @Nested
    class LogicalCombinations {

        @Test
        @SuppressWarnings("unchecked")
        void findByNameAndEmail() throws Exception {
            List<User> result = (List<User>)
                    executeQuery("findByNameAndEmail", new Class[] {String.class, String.class}, new Object[] {
                        "Alice", "alice@example.com"
                    });
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmail()).isEqualTo("alice@example.com");
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByNameOrEmail_union() throws Exception {
            List<User> result = (List<User>)
                    executeQuery("findByNameOrEmail", new Class[] {String.class, String.class}, new Object[] {
                        "Alice", "bob@example.com"
                    });
            // Alice (×2) + Bob (×1)
            assertThat(result).hasSize(3);
        }
    }

    // ============================================================
    // COMPARISONS
    // ============================================================

    @Nested
    class Comparisons {

        @Test
        @SuppressWarnings("unchecked")
        void findByAgeLessThan() throws Exception {
            List<User> result =
                    (List<User>) executeQuery("findByAgeLessThan", new Class[] {Integer.class}, new Object[] {30});
            assertThat(result).hasSize(1).allMatch(u -> u.getAge() < 30);
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByAgeGreaterThan() throws Exception {
            List<User> result =
                    (List<User>) executeQuery("findByAgeGreaterThan", new Class[] {Integer.class}, new Object[] {30});
            assertThat(result).hasSize(2).allMatch(u -> u.getAge() > 30);
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByAgeBetween() throws Exception {
            List<User> result = (List<User>)
                    executeQuery("findByAgeBetween", new Class[] {Integer.class, Integer.class}, new Object[] {25, 35});
            assertThat(result).hasSize(3).allMatch(u -> u.getAge() >= 25 && u.getAge() <= 35);
        }
    }

    // ============================================================
    // NULL CHECKS
    // ============================================================

    @Nested
    class NullChecks {

        @Test
        @SuppressWarnings("unchecked")
        void findByNameIsNotNull_all_non_null() throws Exception {
            List<User> result = (List<User>) executeQuery("findByNameIsNotNull", new Class[] {}, new Object[] {});
            assertThat(result).hasSize(4);
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByNameIsNull_empty() throws Exception {
            List<User> result = (List<User>) executeQuery("findByNameIsNull", new Class[] {}, new Object[] {});
            assertThat(result).isEmpty();
        }
    }

    // ============================================================
    // LIKE VARIANTS
    // ============================================================

    @Nested
    class LikeVariants {

        @Test
        @SuppressWarnings("unchecked")
        void findByNameContaining() throws Exception {
            List<User> result =
                    (List<User>) executeQuery("findByNameContaining", new Class[] {String.class}, new Object[] {"lic"});
            assertThat(result).hasSize(2).allMatch(u -> u.getName().contains("lic"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByNameStartingWith() throws Exception {
            List<User> result = (List<User>)
                    executeQuery("findByNameStartingWith", new Class[] {String.class}, new Object[] {"Al"});
            assertThat(result).hasSize(2).allMatch(u -> u.getName().startsWith("Al"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByNameEndingWith() throws Exception {
            List<User> result =
                    (List<User>) executeQuery("findByNameEndingWith", new Class[] {String.class}, new Object[] {"ob"});
            assertThat(result).hasSize(1).allMatch(u -> u.getName().endsWith("ob"));
        }
    }

    // ============================================================
    // IGNORE CASE
    // ============================================================

    @Nested
    class IgnoreCase {

        @Test
        @SuppressWarnings("unchecked")
        void findByNameIgnoreCase_case_insensitive() throws Exception {
            List<User> result = (List<User>)
                    executeQuery("findByNameIgnoreCase", new Class[] {String.class}, new Object[] {"alice"});
            assertThat(result).hasSize(2);
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByNameContainingIgnoreCase() throws Exception {
            List<User> result = (List<User>)
                    executeQuery("findByNameContainingIgnoreCase", new Class[] {String.class}, new Object[] {"ALICE"});
            assertThat(result).hasSize(2);
        }
    }

    // ============================================================
    // IN
    // ============================================================

    @Nested
    class InOperator {

        @Test
        @SuppressWarnings("unchecked")
        void findByNameIn_collection() throws Exception {
            List<User> result = (List<User>) executeQuery(
                    "findByNameIn", new Class[] {Collection.class}, new Object[] {List.of("Alice", "Bob")});
            assertThat(result).hasSize(3); // 2 Alices + 1 Bob
        }
    }

    // ============================================================
    // COUNT / EXISTS / DELETE
    // ============================================================

    @Nested
    class Aggregates {

        @Test
        void countByName_returns_count() throws Exception {
            Object result = executeQuery("countByName", new Class[] {String.class}, new Object[] {"Alice"});
            assertThat(result).isEqualTo(2L);
        }

        @Test
        void existsByEmail_true_when_found() throws Exception {
            Object result =
                    executeQuery("existsByEmail", new Class[] {String.class}, new Object[] {"alice@example.com"});
            assertThat(result).isEqualTo(true);
        }

        @Test
        void existsByEmail_false_when_not_found() throws Exception {
            Object result = executeQuery("existsByEmail", new Class[] {String.class}, new Object[] {"no@one.com"});
            assertThat(result).isEqualTo(false);
        }

        @Test
        void deleteByName_removes_matching_rows() throws Exception {
            executeQuery("deleteByName", new Class[] {String.class}, new Object[] {"Alice"});
            // Verify deletion via a direct JDBC query
            PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM users WHERE name = ?");
            ps.setString(1, "Alice");
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getLong(1)).isZero();
        }
    }

    // ============================================================
    // SORT / PAGEABLE
    // ============================================================

    @Nested
    class SortAndPageable {

        @Test
        @SuppressWarnings("unchecked")
        void findByNameWithSort_returns_sorted() throws Exception {
            List<User> result =
                    (List<User>) executeQuery("findByName", new Class[] {String.class, Sort.class}, new Object[] {
                        "Alice", Sort.by(Sort.Direction.DESC, "age")
                    });
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getAge())
                    .isGreaterThanOrEqualTo(result.get(1).getAge());
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByActivePageable_returns_page() throws Exception {
            Page<User> page = (Page<User>)
                    executeQuery("findByActive", new Class[] {Boolean.class, Pageable.class}, new Object[] {
                        Boolean.TRUE, PageRequest.of(0, 1)
                    });
            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(2);
        }
    }

    // ============================================================
    // TOP / FIRST LIMIT
    // ============================================================

    @Nested
    class TopLimit {

        @Test
        @SuppressWarnings("unchecked")
        void findTop2ByAgeGreaterThan_respects_limit() throws Exception {
            List<User> result = (List<User>)
                    executeQuery("findTop2ByAgeGreaterThan", new Class[] {Integer.class}, new Object[] {20});
            assertThat(result).hasSizeLessThanOrEqualTo(2);
        }
    }

    // ============================================================
    // SLICE RETURN
    // ============================================================

    @Nested
    class SliceReturn {

        @Test
        @SuppressWarnings("unchecked")
        void findByAgeGreaterThan_slice_with_content() throws Exception {
            Slice<User> result = (Slice<User>)
                    executeQuery("findByAgeGreaterThan", new Class[] {Integer.class, Pageable.class}, new Object[] {
                        20, PageRequest.of(0, 2)
                    });
            assertThat(result.getContent()).hasSizeLessThanOrEqualTo(2);
            assertThat(result.hasContent()).isTrue();
        }

        @Test
        @SuppressWarnings("unchecked")
        void findByAgeGreaterThan_slice_empty() throws Exception {
            Slice<User> result = (Slice<User>)
                    executeQuery("findByAgeGreaterThan", new Class[] {Integer.class, Pageable.class}, new Object[] {
                        999, PageRequest.of(0, 10)
                    });
            assertThat(result.getContent()).isEmpty();
            assertThat(result.hasContent()).isFalse();
        }
    }

    // ============================================================
    // DELETE WITH TYPED RETURN
    // ============================================================

    @Nested
    class TypedDeleteReturn {

        @Test
        void deleteByEmail_returns_long() throws Exception {
            Object result =
                    executeQuery("deleteByEmail", new Class[] {String.class}, new Object[] {"alice@example.com"});
            assertThat(result).isInstanceOf(Long.class);
            assertThat((long) result).isEqualTo(1L);
        }

        @Test
        void deleteByActive_returns_int() throws Exception {
            Object result = executeQuery("deleteByActive", new Class[] {Boolean.class}, new Object[] {Boolean.FALSE});
            assertThat(result).isInstanceOf(Integer.class);
            assertThat((int) result).isEqualTo(2);
        }
    }
}
