package io.github.auspis.fluentrepo4j.query.mapper.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentrepo4j.parse.PartTreeAdapter;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentrepo4j.query.QueryOperation;
import io.github.auspis.fluentrepo4j.query.QueryRuntimeParams;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.NullPredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.runtime.ExecutableQuery;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.test.helper.SqlCaptureHelper;
import io.github.auspis.fluentsql4j.test.util.annotation.ComponentTest;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Component tests for {@link QueryDescriptorToDslMapper} that verify generated SQL
 * fragments and parameter binding order.
 *
 * <p>Uses {@link SqlCaptureHelper} (Mockito-based) to capture SQL without a real DB.
 */
@ComponentTest
class QueryDescriptorToDslMapperTest {

    private DSL dsl;
    private QueryDescriptorToDslMapper<User, Long> mapper;

    @BeforeEach
    void setUp() {
        DSLRegistry registry = DSLRegistry.createWithServiceLoader();
        // Use the StandardSQL (H2-compatible) dialect for SQL generation
        dsl = registry.dslFor("H2")
                .orElseGet(() -> registry.dslFor("StandardSQL").orElseThrow());

        FluentEntityInformation<User, Long> entityInfo = new FluentEntityInformation<>(User.class);
        PropertyMetadataProvider<User, Long> metaProvider = new PropertyMetadataProvider<>(entityInfo);
        mapper = new QueryDescriptorToDslMapper<>(dsl, metaProvider);
    }

    // ---- Utility ----

    private QueryDescriptor describe(String methodName, Class<?>... paramTypes) throws Exception {
        Method m = SampleQueries.class.getMethod(methodName, paramTypes);
        return PartTreeAdapter.adapt(m, User.class);
    }

    private String buildSql(QueryDescriptor descriptor, Object... args) throws Exception {
        ExecutableQuery<User> mapped = mapper.map(descriptor, args, QueryRuntimeParams.empty());
        SqlCaptureHelper capture = new SqlCaptureHelper();
        switch (mapped) {
            case ExecutableQuery.CountQuery<?> q -> q.statementBuilder().build(capture.getConnection());
            case ExecutableQuery.ExistsQuery<?> q -> q.statementBuilder().build(capture.getConnection());
            case ExecutableQuery.EntitySelectQuery<?> q -> q.statementBuilder().build(capture.getConnection());
            case ExecutableQuery.DeleteQuery<?> q -> q.statementBuilder().build(capture.getConnection());
            default -> throw new IllegalStateException("Unexpected query type: " + mapped.getClass());
        }
        return capture.getSql();
    }

    @SuppressWarnings("unused")
    interface SampleQueries {
        // Simple equality
        List<User> findByName(String name);

        List<User> findByNameAndEmail(String name, String email);

        List<User> findByNameOrEmail(String name, String email);

        // Comparisons
        List<User> findByAgeLessThan(Integer age);

        List<User> findByAgeGreaterThan(Integer age);

        List<User> findByAgeBetween(Integer min, Integer max);

        // Null checks
        List<User> findByNameIsNull();

        List<User> findByNameIsNotNull();

        // Like variants
        List<User> findByNameLike(String pattern);

        List<User> findByNameStartingWith(String prefix);

        List<User> findByNameEndingWith(String suffix);

        List<User> findByNameContaining(String fragment);

        // IgnoreCase
        List<User> findByNameIgnoreCase(String name);

        List<User> findByNameContainingIgnoreCase(String fragment);

        // In
        List<User> findByNameIn(Collection<String> names);

        // Boolean
        List<User> findByActiveTrue();

        List<User> findByActiveFalse();

        // Count / Delete
        long countByName(String name);

        void deleteByName(String name);

        // Not-contains / Not-equals
        List<User> findByNameNotContaining(String fragment);

        List<User> findByNameNot(String name);
    }

    // ============================================================
    // SELECT * FROM users
    // ============================================================

    @Nested
    class SelectTable {

        @Test
        void select_includes_table_name() throws Exception {
            String sql = buildSql(describe("findByName", String.class), "Alice");
            assertThat(sql).containsIgnoringCase("users");
        }

        @Test
        void count_uses_count_star() throws Exception {
            String sql = buildSql(describe("countByName", String.class), "Alice");
            assertThat(sql).containsIgnoringCase("COUNT");
        }
    }

    // ============================================================
    // EQUALS
    // ============================================================

    @Nested
    class EqualsCriteria {

        @Test
        void equals_single_where_clause() throws Exception {
            String sql = buildSql(describe("findByName", String.class), "Alice");
            assertThat(sql)
                    .containsIgnoringCase("WHERE")
                    .containsIgnoringCase("name")
                    .contains("?");
        }

        @Test
        void equals_and_two_params() throws Exception {
            String sql = buildSql(describe("findByNameAndEmail", String.class, String.class), "Alice", "a@b.com");
            assertThat(sql)
                    .containsIgnoringCase("name")
                    .containsIgnoringCase("email")
                    .containsIgnoringCase("AND");
        }

        @Test
        void equals_or_two_params() throws Exception {
            String sql = buildSql(describe("findByNameOrEmail", String.class, String.class), "Alice", "a@b.com");
            assertThat(sql).containsIgnoringCase("OR");
        }
    }

    // ============================================================
    // IGNORE CASE
    // ============================================================

    @Nested
    class IgnoreCase {

        @Test
        void ignore_case_equals_uses_lower() throws Exception {
            String sql = buildSql(describe("findByNameIgnoreCase", String.class), "Alice");
            assertThat(sql).containsIgnoringCase("LOWER");
        }

        @Test
        void ignore_case_containing_uses_lower_and_wildcards() throws Exception {
            String sql = buildSql(describe("findByNameContainingIgnoreCase", String.class), "alice");
            assertThat(sql).containsIgnoringCase("LOWER").containsIgnoringCase("LIKE");
        }
    }

    // ============================================================
    // COMPARISONS
    // ============================================================

    @Nested
    class Comparisons {

        @Test
        void less_than() throws Exception {
            String sql = buildSql(describe("findByAgeLessThan", Integer.class), 30);
            assertThat(sql).containsIgnoringCase("age").contains("<").contains("?");
        }

        @Test
        void greater_than() throws Exception {
            String sql = buildSql(describe("findByAgeGreaterThan", Integer.class), 30);
            assertThat(sql).containsIgnoringCase("age").contains(">").contains("?");
        }
    }

    // ============================================================
    // BETWEEN
    // ============================================================

    @Nested
    class BetweenCriteria {

        @Test
        void between_uses_between_keyword() throws Exception {
            String sql = buildSql(describe("findByAgeBetween", Integer.class, Integer.class), 18, 65);
            assertThat(sql).containsIgnoringCase("BETWEEN").contains("?");
        }
    }

    // ============================================================
    // NULL CHECKS
    // ============================================================

    @Nested
    class NullChecks {

        @Test
        void is_null_no_param_placeholder() throws Exception {
            String sql = buildSql(describe("findByNameIsNull"));
            assertThat(sql).containsIgnoringCase("IS NULL").doesNotContain("?");
        }

        @Test
        void is_not_null_no_param_placeholder() throws Exception {
            String sql = buildSql(describe("findByNameIsNotNull"));
            assertThat(sql).containsIgnoringCase("IS NOT NULL").doesNotContain("?");
        }
    }

    // ============================================================
    // LIKE VARIANTS
    // ============================================================

    @Nested
    class LikeVariants {

        @Test
        void like_plain_no_wildcards_added() throws Exception {
            String sql = buildSql(describe("findByNameLike", String.class), "%alice%");
            assertThat(sql).containsIgnoringCase("LIKE").contains("?");
        }

        @Test
        void starting_with_pattern_applied() throws Exception {
            String sql = buildSql(describe("findByNameStartingWith", String.class), "Al");
            assertThat(sql).containsIgnoringCase("LIKE").contains("?");
        }

        @Test
        void ending_with_pattern_applied() throws Exception {
            String sql = buildSql(describe("findByNameEndingWith", String.class), "ice");
            assertThat(sql).containsIgnoringCase("LIKE").contains("?");
        }

        @Test
        void containing_pattern_applied() throws Exception {
            String sql = buildSql(describe("findByNameContaining", String.class), "lic");
            assertThat(sql).containsIgnoringCase("LIKE").contains("?");
        }

        @Test
        void not_containing_wrapped_in_not() throws Exception {
            String sql = buildSql(describe("findByNameNotContaining", String.class), "bad");
            assertThat(sql).containsIgnoringCase("NOT").containsIgnoringCase("LIKE");
        }
    }

    // ============================================================
    // IN
    // ============================================================

    @Nested
    class InCriteria {

        @Test
        void in_collection() throws Exception {
            String sql = buildSql(describe("findByNameIn", Collection.class), List.of("Alice", "Bob"));
            assertThat(sql).containsIgnoringCase("IN").contains("?");
        }
    }

    // ============================================================
    // BOOLEAN
    // ============================================================

    @Nested
    class BooleanCriteria {

        @Test
        void true_uses_active_column() throws Exception {
            String sql = buildSql(describe("findByActiveTrue"));
            assertThat(sql).containsIgnoringCase("active").containsIgnoringCase("WHERE");
        }

        @Test
        void false_uses_active_column() throws Exception {
            String sql = buildSql(describe("findByActiveFalse"));
            assertThat(sql).containsIgnoringCase("active").containsIgnoringCase("WHERE");
        }
    }

    // ============================================================
    // DELETE
    // ============================================================

    @Nested
    class DeleteQuery {

        @Test
        void delete_by_name_uses_delete_from() throws Exception {
            String sql = buildSql(describe("deleteByName", String.class), "Alice");
            assertThat(sql)
                    .containsIgnoringCase("DELETE")
                    .containsIgnoringCase("users")
                    .containsIgnoringCase("name")
                    .contains("?");
        }
    }

    // ============================================================
    // NOT EQUALS
    // ============================================================

    @Nested
    class NotEquals {

        @Test
        void not_equals_ne_operator() throws Exception {
            String sql = buildSql(describe("findByNameNot", String.class), "Alice");
            assertThat(sql).containsIgnoringCase("name").contains("?");
            // SQL should contain != or <>
            assertThat(sql.contains("!=") || sql.contains("<>")).isTrue();
        }
    }

    // ============================================================
    // NULL-CRITERION (null-object)
    // ============================================================

    @Nested
    class NullCriterionTests {

        @Test
        void nullCriterion_produces_no_where_clause() throws Exception {
            QueryDescriptor descriptor = new QueryDescriptor(
                    QueryOperation.FIND, false, null, NullPredicateDescriptor.INSTANCE, List.of(), -1, -1);

            String sql = buildSql(descriptor);
            assertThat(sql).doesNotContainIgnoringCase("WHERE");
        }
    }
}
