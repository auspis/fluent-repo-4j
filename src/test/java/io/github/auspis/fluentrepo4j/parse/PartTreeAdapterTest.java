package io.github.auspis.fluentrepo4j.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentrepo4j.query.QueryOperation;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.CompositePredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.CompositePredicateDescriptor.CompositeType;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.PropertyPredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptorOperator;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.ast.dql.clause.Sorting;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for {@link PartTreeAdapter} covering all Spring Data operator
 * types and edge cases.
 */
class PartTreeAdapterTest {

    // ---- Helper interfaces ----

    @SuppressWarnings("unused")
    interface UserQueryMethods {
        // Equals / NotEquals
        List<User> findByName(String name);

        List<User> findByNameNot(String name);

        // Logical combinations
        List<User> findByNameAndEmail(String name, String email);

        List<User> findByNameOrEmail(String name, String email);

        List<User> findByNameAndEmailOrAge(String name, String email, Integer age);

        // Comparisons
        List<User> findByAgeLessThan(Integer age);

        List<User> findByAgeLessThanEqual(Integer age);

        List<User> findByAgeGreaterThan(Integer age);

        List<User> findByAgeGreaterThanEqual(Integer age);

        // Before / After (aliases for LT / GT on dates)
        List<User> findByBirthdateBefore(java.time.LocalDate date);

        List<User> findByBirthdateAfter(java.time.LocalDate date);

        // Between
        List<User> findByAgeBetween(Integer min, Integer max);

        // Null checks (0 params)
        List<User> findByNameIsNull();

        List<User> findByNameIsNotNull();

        // Like variants
        List<User> findByNameLike(String pattern);

        List<User> findByNameNotLike(String pattern);

        List<User> findByNameStartingWith(String prefix);

        List<User> findByNameEndingWith(String suffix);

        List<User> findByNameContaining(String fragment);

        List<User> findByNameNotContaining(String fragment);

        // IgnoreCase
        List<User> findByNameIgnoreCase(String name);

        List<User> findByNameContainingIgnoreCase(String fragment);

        // In / NotIn
        List<User> findByNameIn(Collection<String> names);

        List<User> findByNameNotIn(Collection<String> names);

        // Boolean
        List<User> findByActiveTrue();

        List<User> findByActiveFalse();

        // Distinct
        List<User> findDistinctByName(String name);

        // Limiting (Top/First)
        List<User> findTop3ByName(String name);

        List<User> findFirst1ByName(String name);

        // Operations
        long countByName(String name);

        boolean existsByEmail(String email);

        void deleteByName(String name);

        // Sort / Pageable params
        List<User> findByNameOrderByAgeAsc(String name);

        List<User> findByAge(Integer age, Sort sort);

        Page<User> findByActive(Boolean active, Pageable pageable);
    }

    // ---- Utility ----

    private static QueryDescriptor adapt(String methodName, Class<?>... paramTypes) throws Exception {
        Method m = UserQueryMethods.class.getMethod(methodName, paramTypes);
        return PartTreeAdapter.adapt(m, User.class);
    }

    private static PropertyPredicateDescriptor rootProperty(QueryDescriptor d) {
        assertThat(d.predicateDescriptor()).isInstanceOf(PropertyPredicateDescriptor.class);
        return (PropertyPredicateDescriptor) d.predicateDescriptor();
    }

    // ============================================================
    // OPERATION detection
    // ============================================================

    @Nested
    class OperationDetection {

        @Test
        void find_operation() throws Exception {
            QueryDescriptor d = adapt("findByName", String.class);
            assertThat(d.operation()).isEqualTo(QueryOperation.FIND);
        }

        @Test
        void count_operation() throws Exception {
            QueryDescriptor d = adapt("countByName", String.class);
            assertThat(d.operation()).isEqualTo(QueryOperation.COUNT);
        }

        @Test
        void exists_operation() throws Exception {
            QueryDescriptor d = adapt("existsByEmail", String.class);
            assertThat(d.operation()).isEqualTo(QueryOperation.EXISTS);
        }

        @Test
        void delete_operation() throws Exception {
            QueryDescriptor d = adapt("deleteByName", String.class);
            assertThat(d.operation()).isEqualTo(QueryOperation.DELETE);
        }
    }

    // ============================================================
    // DISTINCT / LIMIT
    // ============================================================

    @Nested
    class DistinctAndLimit {

        @Test
        void distinct_flag() throws Exception {
            QueryDescriptor d = adapt("findDistinctByName", String.class);
            assertThat(d.distinct()).isTrue();
            assertThat(d.operation()).isEqualTo(QueryOperation.FIND);
        }

        @Test
        void top3_limit() throws Exception {
            QueryDescriptor d = adapt("findTop3ByName", String.class);
            assertThat(d.maxResults()).isEqualTo(3);
        }

        @Test
        void first1_limit() throws Exception {
            QueryDescriptor d = adapt("findFirst1ByName", String.class);
            assertThat(d.maxResults()).isEqualTo(1);
        }
    }

    // ============================================================
    // SIMPLE PROPERTY (equals / not equals)
    // ============================================================

    @Nested
    class SimpleProperty {

        @Test
        void equals_single() throws Exception {
            QueryDescriptor d = adapt("findByName", String.class);
            PropertyPredicateDescriptor pc = rootProperty(d);
            assertThat(pc.name()).isEqualTo("name");
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.EQUALS);
            assertThat(pc.paramIndex()).isZero();
            assertThat(pc.paramCount()).isEqualTo(1);
            assertThat(pc.ignoreCase()).isFalse();
        }

        @Test
        void not_equals() throws Exception {
            QueryDescriptor d = adapt("findByNameNot", String.class);
            PropertyPredicateDescriptor pc = rootProperty(d);
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.NOT_EQUALS);
        }

        @Test
        void ignore_case_equals() throws Exception {
            QueryDescriptor d = adapt("findByNameIgnoreCase", String.class);
            PropertyPredicateDescriptor pc = rootProperty(d);
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.EQUALS);
            assertThat(pc.ignoreCase()).isTrue();
        }
    }

    // ============================================================
    // LOGICAL COMBINATORS
    // ============================================================

    @Nested
    class LogicalCombinators {

        @Test
        void and_combination() throws Exception {
            QueryDescriptor d = adapt("findByNameAndEmail", String.class, String.class);
            assertThat(d.predicateDescriptor()).isInstanceOf(CompositePredicateDescriptor.class);
            CompositePredicateDescriptor cc = (CompositePredicateDescriptor) d.predicateDescriptor();
            assertThat(cc.type()).isEqualTo(CompositeType.AND);
            assertThat(cc.children()).hasSize(2);

            PropertyPredicateDescriptor name =
                    (PropertyPredicateDescriptor) cc.children().get(0);
            assertThat(name.name()).isEqualTo("name");
            assertThat(name.paramIndex()).isZero();

            PropertyPredicateDescriptor email =
                    (PropertyPredicateDescriptor) cc.children().get(1);
            assertThat(email.name()).isEqualTo("email");
            assertThat(email.paramIndex()).isEqualTo(1);
        }

        @Test
        void or_combination() throws Exception {
            QueryDescriptor d = adapt("findByNameOrEmail", String.class, String.class);
            assertThat(d.predicateDescriptor()).isInstanceOf(CompositePredicateDescriptor.class);
            CompositePredicateDescriptor cc = (CompositePredicateDescriptor) d.predicateDescriptor();
            assertThat(cc.type()).isEqualTo(CompositeType.OR);
            assertThat(cc.children()).hasSize(2);
        }

        @Test
        void and_or_mixed() throws Exception {
            QueryDescriptor d = adapt("findByNameAndEmailOrAge", String.class, String.class, Integer.class);
            // PartTree: (name AND email) OR age
            assertThat(d.predicateDescriptor()).isInstanceOf(CompositePredicateDescriptor.class);
            CompositePredicateDescriptor or = (CompositePredicateDescriptor) d.predicateDescriptor();
            assertThat(or.type()).isEqualTo(CompositeType.OR);
            assertThat(or.children()).hasSize(2);

            // First child: AND(name, email)
            CompositePredicateDescriptor and =
                    (CompositePredicateDescriptor) or.children().get(0);
            assertThat(and.type()).isEqualTo(CompositeType.AND);
            assertThat(and.children()).hasSize(2);

            // Parameter indices: name=0, email=1, age=2
            PropertyPredicateDescriptor namePc =
                    (PropertyPredicateDescriptor) and.children().get(0);
            assertThat(namePc.paramIndex()).isZero();

            PropertyPredicateDescriptor emailPc =
                    (PropertyPredicateDescriptor) and.children().get(1);
            assertThat(emailPc.paramIndex()).isEqualTo(1);

            PropertyPredicateDescriptor agePc =
                    (PropertyPredicateDescriptor) or.children().get(1);
            assertThat(agePc.paramIndex()).isEqualTo(2);
        }
    }

    // ============================================================
    // COMPARISONS
    // ============================================================

    @Nested
    class Comparisons {

        @Test
        void less_than() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByAgeLessThan", Integer.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.LESS_THAN);
        }

        @Test
        void less_than_equal() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByAgeLessThanEqual", Integer.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.LESS_THAN_EQUAL);
        }

        @Test
        void greater_than() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByAgeGreaterThan", Integer.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.GREATER_THAN);
        }

        @Test
        void greater_than_equal() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByAgeGreaterThanEqual", Integer.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.GREATER_THAN_EQUAL);
        }

        @Test
        void before_maps_to_less_than() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByBirthdateBefore", java.time.LocalDate.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.LESS_THAN);
        }

        @Test
        void after_maps_to_greater_than() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByBirthdateAfter", java.time.LocalDate.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.GREATER_THAN);
        }
    }

    // ============================================================
    // BETWEEN
    // ============================================================

    @Nested
    class BetweenOperator {

        @Test
        void between_two_params() throws Exception {
            QueryDescriptor d = adapt("findByAgeBetween", Integer.class, Integer.class);
            PropertyPredicateDescriptor pc = rootProperty(d);
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.BETWEEN);
            assertThat(pc.paramIndex()).isZero();
            assertThat(pc.paramCount()).isEqualTo(2);
        }
    }

    // ============================================================
    // NULL / NOT NULL
    // ============================================================

    @Nested
    class NullChecks {

        @Test
        void is_null_zero_params() throws Exception {
            QueryDescriptor d = adapt("findByNameIsNull");
            PropertyPredicateDescriptor pc = rootProperty(d);
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.IS_NULL);
            assertThat(pc.paramCount()).isZero();
        }

        @Test
        void is_not_null_zero_params() throws Exception {
            QueryDescriptor d = adapt("findByNameIsNotNull");
            PropertyPredicateDescriptor pc = rootProperty(d);
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.IS_NOT_NULL);
            assertThat(pc.paramCount()).isZero();
        }
    }

    // ============================================================
    // LIKE VARIANTS
    // ============================================================

    @Nested
    class LikeVariants {

        @Test
        void like() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByNameLike", String.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.LIKE);
        }

        @Test
        void not_like() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByNameNotLike", String.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.NOT_LIKE);
        }

        @Test
        void starting_with() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByNameStartingWith", String.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.STARTING_WITH);
        }

        @Test
        void ending_with() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByNameEndingWith", String.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.ENDING_WITH);
        }

        @Test
        void containing() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByNameContaining", String.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.CONTAINING);
        }

        @Test
        void not_containing() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByNameNotContaining", String.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.NOT_CONTAINING);
        }

        @Test
        void containing_ignore_case() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByNameContainingIgnoreCase", String.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.CONTAINING);
            assertThat(pc.ignoreCase()).isTrue();
        }
    }

    // ============================================================
    // IN / NOT IN
    // ============================================================

    @Nested
    class InOperator {

        @Test
        void in_collection() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByNameIn", Collection.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.IN);
            assertThat(pc.paramIndex()).isZero();
        }

        @Test
        void not_in_collection() throws Exception {
            PropertyPredicateDescriptor pc = rootProperty(adapt("findByNameNotIn", Collection.class));
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.NOT_IN);
        }
    }

    // ============================================================
    // TRUE / FALSE
    // ============================================================

    @Nested
    class BooleanChecks {

        @Test
        void true_check_zero_params() throws Exception {
            QueryDescriptor d = adapt("findByActiveTrue");
            PropertyPredicateDescriptor pc = rootProperty(d);
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.TRUE);
            assertThat(pc.paramCount()).isZero();
        }

        @Test
        void false_check_zero_params() throws Exception {
            QueryDescriptor d = adapt("findByActiveFalse");
            PropertyPredicateDescriptor pc = rootProperty(d);
            assertThat(pc.operator()).isEqualTo(PredicateDescriptorOperator.FALSE);
            assertThat(pc.paramCount()).isZero();
        }
    }

    // ============================================================
    // ORDER BY (static, from method name)
    // ============================================================

    @Nested
    class OrderBy {

        @Test
        void order_by_in_method_name() throws Exception {
            QueryDescriptor d = adapt("findByNameOrderByAgeAsc", String.class);
            assertThat(d.orderBy()).hasSize(1);
            assertThat(d.orderBy().get(0).columnName()).isEqualTo("age");
            assertThat(d.orderBy().get(0).direction()).isEqualTo(Sorting.SortOrder.ASC);
        }
    }

    // ============================================================
    // PAGEABLE / SORT parameter indices
    // ============================================================

    @Nested
    class SpecialParams {

        @Test
        void sort_param_index() throws Exception {
            QueryDescriptor d = adapt("findByAge", Integer.class, Sort.class);
            assertThat(d.sortParamIndex()).isEqualTo(1);
            assertThat(d.pageableParamIndex()).isEqualTo(-1);

            // age criterion should use index 0 (sort param skipped)
            PropertyPredicateDescriptor pc = rootProperty(d);
            assertThat(pc.paramIndex()).isZero();
        }

        @Test
        void pageable_param_index() throws Exception {
            QueryDescriptor d = adapt("findByActive", Boolean.class, Pageable.class);
            assertThat(d.pageableParamIndex()).isEqualTo(1);
            assertThat(d.sortParamIndex()).isEqualTo(-1);

            // active criterion should use index 0
            PropertyPredicateDescriptor pc = rootProperty(d);
            assertThat(pc.paramIndex()).isZero();
        }
    }

    // ============================================================
    // UNSUPPORTED OPERATOR
    // ============================================================

    @Nested
    class UnsupportedOperators {

        @SuppressWarnings("unused")
        interface RegexMethods {
            List<User> findByNameMatchesRegex(String pattern);
        }

        @Test
        void regex_throws_unsupported() throws NoSuchMethodException, SecurityException {
            Method m = RegexMethods.class.getMethod("findByNameMatchesRegex", String.class);
            assertThatThrownBy(() -> PartTreeAdapter.adapt(m, User.class))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("REGEX");
        }
    }
}
