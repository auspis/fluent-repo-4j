package io.github.auspis.fluentrepo4j.query.mapper.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.CompositePredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.CompositePredicateDescriptor.CompositeType;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.NullPredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptorOperator;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PropertyPredicateDescriptor;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.ast.core.predicate.AndOr;
import io.github.auspis.fluentsql4j.ast.core.predicate.Between;
import io.github.auspis.fluentsql4j.ast.core.predicate.Comparison;
import io.github.auspis.fluentsql4j.ast.core.predicate.In;
import io.github.auspis.fluentsql4j.ast.core.predicate.IsNotNull;
import io.github.auspis.fluentsql4j.ast.core.predicate.IsNull;
import io.github.auspis.fluentsql4j.ast.core.predicate.Like;
import io.github.auspis.fluentsql4j.ast.core.predicate.Not;
import io.github.auspis.fluentsql4j.ast.core.predicate.NullPredicate;
import io.github.auspis.fluentsql4j.ast.core.predicate.Predicate;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PredicateDescriptorMapper}.
 *
 * <p>Verifies sealed-switch dispatch, all 18 operators, case-insensitive
 * variants, composite edge cases, and IN collection/array handling.
 */
class PredicateDescriptorMapperTest {

    private PredicateDescriptorMapper mapper;
    private PropertyMetadataProvider<User, Long> metaProvider;

    @BeforeEach
    void setUp() {
        FluentEntityInformation<User, Long> entityInfo = new FluentEntityInformation<>(User.class);
        metaProvider = new PropertyMetadataProvider<>(entityInfo);
        mapper = new PredicateDescriptorMapper();
    }

    // ---- Helpers ----

    private PropertyPredicateDescriptor leaf(
            String property, PredicateDescriptorOperator op, int paramIndex, int paramCount) {
        return new PropertyPredicateDescriptor(property, op, false, paramIndex, paramCount);
    }

    private PropertyPredicateDescriptor leafIgnoreCase(
            String property, PredicateDescriptorOperator op, int paramIndex, int paramCount) {
        return new PropertyPredicateDescriptor(property, op, true, paramIndex, paramCount);
    }

    // ---- A) Sealed dispatch ----

    @Nested
    class SealedDispatch {

        @Test
        void nullPredicateDescriptor_returnsNullPredicate() {
            Predicate result = mapper.map(NullPredicateDescriptor.INSTANCE, metaProvider, new Object[] {});

            assertThat(result).isInstanceOf(NullPredicate.class);
        }

        @Test
        void propertyPredicateDescriptor_returnsComparison() {
            PropertyPredicateDescriptor descriptor = leaf("name", PredicateDescriptorOperator.EQUALS, 0, 1);

            Predicate result = mapper.map(descriptor, metaProvider, new Object[] {"Alice"});

            assertThat(result).isInstanceOf(Comparison.class);
        }

        @Test
        void compositePredicateDescriptor_returnsAndOr() {
            CompositePredicateDescriptor descriptor = new CompositePredicateDescriptor(
                    CompositeType.AND,
                    List.of(
                            leaf("name", PredicateDescriptorOperator.EQUALS, 0, 1),
                            leaf("email", PredicateDescriptorOperator.EQUALS, 1, 1)));

            Predicate result = mapper.map(descriptor, metaProvider, new Object[] {"Alice", "alice@test.com"});

            assertThat(result).isInstanceOf(AndOr.class);
        }
    }

    // ---- B) All 18 operators ----

    @Nested
    class OperatorMapping {

        @Test
        void equals_produces_comparison() {
            Predicate result = mapper.map(
                    leaf("name", PredicateDescriptorOperator.EQUALS, 0, 1), metaProvider, new Object[] {"Alice"});

            assertThat(result).isInstanceOf(Comparison.class);
            assertThat(((Comparison) result).operator()).isEqualTo(Comparison.ComparisonOperator.EQUALS);
        }

        @Test
        void notEquals_produces_comparison() {
            Predicate result = mapper.map(
                    leaf("name", PredicateDescriptorOperator.NOT_EQUALS, 0, 1), metaProvider, new Object[] {"Alice"});

            assertThat(result).isInstanceOf(Comparison.class);
            assertThat(((Comparison) result).operator()).isEqualTo(Comparison.ComparisonOperator.NOT_EQUALS);
        }

        @Test
        void lessThan_produces_comparison() {
            Predicate result = mapper.map(
                    leaf("age", PredicateDescriptorOperator.LESS_THAN, 0, 1), metaProvider, new Object[] {30});

            assertThat(result).isInstanceOf(Comparison.class);
            assertThat(((Comparison) result).operator()).isEqualTo(Comparison.ComparisonOperator.LESS_THAN);
        }

        @Test
        void lessThanEqual_produces_comparison() {
            Predicate result = mapper.map(
                    leaf("age", PredicateDescriptorOperator.LESS_THAN_EQUAL, 0, 1), metaProvider, new Object[] {30});

            assertThat(result).isInstanceOf(Comparison.class);
            assertThat(((Comparison) result).operator()).isEqualTo(Comparison.ComparisonOperator.LESS_THAN_OR_EQUALS);
        }

        @Test
        void greaterThan_produces_comparison() {
            Predicate result = mapper.map(
                    leaf("age", PredicateDescriptorOperator.GREATER_THAN, 0, 1), metaProvider, new Object[] {30});

            assertThat(result).isInstanceOf(Comparison.class);
            assertThat(((Comparison) result).operator()).isEqualTo(Comparison.ComparisonOperator.GREATER_THAN);
        }

        @Test
        void greaterThanEqual_produces_comparison() {
            Predicate result = mapper.map(
                    leaf("age", PredicateDescriptorOperator.GREATER_THAN_EQUAL, 0, 1), metaProvider, new Object[] {30});

            assertThat(result).isInstanceOf(Comparison.class);
            assertThat(((Comparison) result).operator())
                    .isEqualTo(Comparison.ComparisonOperator.GREATER_THAN_OR_EQUALS);
        }

        @Test
        void between_produces_between() {
            Predicate result = mapper.map(
                    leaf("age", PredicateDescriptorOperator.BETWEEN, 0, 2), metaProvider, new Object[] {18, 65});

            assertThat(result).isInstanceOf(Between.class);
        }

        @Test
        void isNull_produces_isNull() {
            Predicate result =
                    mapper.map(leaf("name", PredicateDescriptorOperator.IS_NULL, 0, 0), metaProvider, new Object[] {});

            assertThat(result).isInstanceOf(IsNull.class);
        }

        @Test
        void isNotNull_produces_isNotNull() {
            Predicate result = mapper.map(
                    leaf("name", PredicateDescriptorOperator.IS_NOT_NULL, 0, 0), metaProvider, new Object[] {});

            assertThat(result).isInstanceOf(IsNotNull.class);
        }

        @Test
        void like_produces_like() {
            Predicate result = mapper.map(
                    leaf("name", PredicateDescriptorOperator.LIKE, 0, 1), metaProvider, new Object[] {"%test%"});

            assertThat(result).isInstanceOf(Like.class);
            assertThat(((Like) result).pattern()).isEqualTo("%test%");
        }

        @Test
        void notLike_produces_notWrappingLike() {
            Predicate result = mapper.map(
                    leaf("name", PredicateDescriptorOperator.NOT_LIKE, 0, 1), metaProvider, new Object[] {"%test%"});

            assertThat(result).isInstanceOf(Not.class);
            assertThat(((Not) result).expression()).isInstanceOf(Like.class);
        }

        @Test
        void startingWith_appends_suffix_wildcard() {
            Predicate result = mapper.map(
                    leaf("name", PredicateDescriptorOperator.STARTING_WITH, 0, 1), metaProvider, new Object[] {"Al"});

            assertThat(result).isInstanceOf(Like.class);
            assertThat(((Like) result).pattern()).isEqualTo("Al%");
        }

        @Test
        void endingWith_prepends_prefix_wildcard() {
            Predicate result = mapper.map(
                    leaf("name", PredicateDescriptorOperator.ENDING_WITH, 0, 1), metaProvider, new Object[] {"ice"});

            assertThat(result).isInstanceOf(Like.class);
            assertThat(((Like) result).pattern()).isEqualTo("%ice");
        }

        @Test
        void containing_wraps_with_wildcards() {
            Predicate result = mapper.map(
                    leaf("name", PredicateDescriptorOperator.CONTAINING, 0, 1), metaProvider, new Object[] {"lic"});

            assertThat(result).isInstanceOf(Like.class);
            assertThat(((Like) result).pattern()).isEqualTo("%lic%");
        }

        @Test
        void notContaining_produces_notWrappingLike() {
            Predicate result = mapper.map(
                    leaf("name", PredicateDescriptorOperator.NOT_CONTAINING, 0, 1), metaProvider, new Object[] {"lic"});

            assertThat(result).isInstanceOf(Not.class);
            Not not = (Not) result;
            assertThat(not.expression()).isInstanceOf(Like.class);
            assertThat(((Like) not.expression()).pattern()).isEqualTo("%lic%");
        }

        @Test
        void in_produces_in() {
            Predicate result =
                    mapper.map(leaf("name", PredicateDescriptorOperator.IN, 0, 1), metaProvider, new Object[] {
                        List.of("Alice", "Bob")
                    });

            assertThat(result).isInstanceOf(In.class);
            assertThat(((In) result).values()).hasSize(2);
        }

        @Test
        void notIn_produces_notWrappingIn() {
            Predicate result =
                    mapper.map(leaf("name", PredicateDescriptorOperator.NOT_IN, 0, 1), metaProvider, new Object[] {
                        List.of("Alice", "Bob")
                    });

            assertThat(result).isInstanceOf(Not.class);
            assertThat(((Not) result).expression()).isInstanceOf(In.class);
        }

        @Test
        void true_produces_equalsTrue() {
            Predicate result =
                    mapper.map(leaf("active", PredicateDescriptorOperator.TRUE, 0, 0), metaProvider, new Object[] {});

            assertThat(result).isInstanceOf(Comparison.class);
            assertThat(((Comparison) result).operator()).isEqualTo(Comparison.ComparisonOperator.EQUALS);
        }

        @Test
        void false_produces_equalsFalse() {
            Predicate result =
                    mapper.map(leaf("active", PredicateDescriptorOperator.FALSE, 0, 0), metaProvider, new Object[] {});

            assertThat(result).isInstanceOf(Comparison.class);
            assertThat(((Comparison) result).operator()).isEqualTo(Comparison.ComparisonOperator.EQUALS);
        }
    }

    // ---- C) Case-insensitive variants ----

    @Nested
    class CaseInsensitive {

        @Test
        void ignoreCase_equals_wraps_with_lower() {
            PropertyPredicateDescriptor descriptor = leafIgnoreCase("name", PredicateDescriptorOperator.EQUALS, 0, 1);

            Predicate result = mapper.map(descriptor, metaProvider, new Object[] {"Alice"});

            assertThat(result).isInstanceOf(Comparison.class);
            // LHS should be a LOWER() function wrapping the column, not a raw column reference
            Comparison comparison = (Comparison) result;
            assertThat(comparison.lhs()).isNotNull();
            assertThat(comparison.lhs().getClass().getSimpleName()).isEqualTo("UnaryString");
        }

        @Test
        void ignoreCase_containing_wraps_with_lower_and_wildcards() {
            PropertyPredicateDescriptor descriptor =
                    leafIgnoreCase("name", PredicateDescriptorOperator.CONTAINING, 0, 1);

            Predicate result = mapper.map(descriptor, metaProvider, new Object[] {"LIC"});

            assertThat(result).isInstanceOf(Like.class);
            Like like = (Like) result;
            // Pattern should be lowercased and wrapped in wildcards
            assertThat(like.pattern()).isEqualTo("%lic%");
        }
    }

    // ---- D) Composite edge cases ----

    @Nested
    class CompositeEdgeCases {

        @Test
        void and_with_two_real_predicates_produces_andOr() {
            CompositePredicateDescriptor descriptor = new CompositePredicateDescriptor(
                    CompositeType.AND,
                    List.of(
                            leaf("name", PredicateDescriptorOperator.EQUALS, 0, 1),
                            leaf("email", PredicateDescriptorOperator.EQUALS, 1, 1)));

            Predicate result = mapper.map(descriptor, metaProvider, new Object[] {"Alice", "alice@test.com"});

            assertThat(result).isInstanceOf(AndOr.class);
            AndOr andOr = (AndOr) result;
            assertThat(andOr.operands()).hasSize(2);
        }

        @Test
        void or_with_mixed_real_and_null_filters_null_children() {
            CompositePredicateDescriptor descriptor = new CompositePredicateDescriptor(
                    CompositeType.OR,
                    List.of(leaf("name", PredicateDescriptorOperator.EQUALS, 0, 1), NullPredicateDescriptor.INSTANCE));

            Predicate result = mapper.map(descriptor, metaProvider, new Object[] {"Alice"});

            // NullPredicateDescriptor child is filtered out; single remaining child
            // should still produce the real predicate (implementations may vary —
            // AndOr.or with 1 element or single Comparison depending on size)
            assertThat(result).isNotInstanceOf(NullPredicate.class);
        }

        @Test
        void allNullChildren_produces_nullPredicate() {
            CompositePredicateDescriptor descriptor = new CompositePredicateDescriptor(
                    CompositeType.AND, List.of(NullPredicateDescriptor.INSTANCE, NullPredicateDescriptor.INSTANCE));

            Predicate result = mapper.map(descriptor, metaProvider, new Object[] {});

            assertThat(result).isInstanceOf(NullPredicate.class);
        }
    }

    // ---- E) IN edge cases ----

    @Nested
    class InEdgeCases {

        @Test
        void in_with_collection_input() {
            Predicate result =
                    mapper.map(leaf("name", PredicateDescriptorOperator.IN, 0, 1), metaProvider, new Object[] {
                        List.of("Alice", "Bob", "Charlie")
                    });

            assertThat(result).isInstanceOf(In.class);
            assertThat(((In) result).values()).hasSize(3);
        }

        @Test
        void in_with_array_input() {
            Object[] names = new Object[] {"Alice", "Bob"};

            Predicate result =
                    mapper.map(leaf("name", PredicateDescriptorOperator.IN, 0, 1), metaProvider, new Object[] {names});

            assertThat(result).isInstanceOf(In.class);
            assertThat(((In) result).values()).hasSize(2);
        }
    }
}
