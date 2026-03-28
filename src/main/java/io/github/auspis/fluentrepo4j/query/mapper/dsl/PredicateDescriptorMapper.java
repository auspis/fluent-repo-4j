package io.github.auspis.fluentrepo4j.query.mapper.dsl;

import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.CompositePredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.NullPredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PropertyPredicateDescriptor;
import io.github.auspis.fluentsql4j.ast.core.expression.ValueExpression;
import io.github.auspis.fluentsql4j.ast.core.expression.function.string.UnaryString;
import io.github.auspis.fluentsql4j.ast.core.expression.scalar.ColumnReference;
import io.github.auspis.fluentsql4j.ast.core.expression.scalar.ScalarExpression;
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
import io.github.auspis.fluentsql4j.dsl.util.LiteralUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Maps a {@link PredicateDescriptor} tree into the corresponding fluent-sql-4j
 * {@link Predicate} AST.
 *
 * <p>This class centralises the translation logic that was previously embedded
 * inside each {@code PredicateDescriptor} implementation, keeping the descriptor
 * hierarchy as pure data carriers.
 */
public class PredicateDescriptorMapper {

    public Predicate map(
            PredicateDescriptor descriptor, PropertyMetadataProvider<?, ?> metadataProvider, Object[] args) {
        return switch (descriptor) {
            case PropertyPredicateDescriptor d -> mapProperty(d, metadataProvider, args);
            case CompositePredicateDescriptor d -> mapComposite(d, metadataProvider, args);
            case NullPredicateDescriptor d -> new NullPredicate();
        };
    }

    private Predicate mapProperty(
            PropertyPredicateDescriptor descriptor, PropertyMetadataProvider<?, ?> metadataProvider, Object[] args) {
        String column = metadataProvider.resolveColumn(descriptor.name());
        ColumnReference colRef = ColumnReference.of(null, column);

        return switch (descriptor.operator()) {
            case EQUALS -> buildEquals(descriptor, colRef, args);
            case NOT_EQUALS -> buildNotEquals(descriptor, colRef, args);
            case LESS_THAN -> Comparison.lt(colRef, LiteralUtil.createLiteral(args[descriptor.paramIndex()]));
            case LESS_THAN_EQUAL -> Comparison.lte(colRef, LiteralUtil.createLiteral(args[descriptor.paramIndex()]));
            case GREATER_THAN -> Comparison.gt(colRef, LiteralUtil.createLiteral(args[descriptor.paramIndex()]));
            case GREATER_THAN_EQUAL -> Comparison.gte(colRef, LiteralUtil.createLiteral(args[descriptor.paramIndex()]));
            case BETWEEN -> buildBetween(descriptor, colRef, args);
            case IS_NULL -> new IsNull(colRef);
            case IS_NOT_NULL -> new IsNotNull(colRef);
            case LIKE -> buildLike(descriptor, colRef, args, "like");
            case NOT_LIKE -> new Not(buildLike(descriptor, colRef, args, "like"));
            case STARTING_WITH -> buildLike(descriptor, colRef, args, "starting");
            case ENDING_WITH -> buildLike(descriptor, colRef, args, "ending");
            case CONTAINING -> buildLike(descriptor, colRef, args, "containing");
            case NOT_CONTAINING -> new Not(buildLike(descriptor, colRef, args, "containing"));
            case IN -> buildIn(descriptor, colRef, args);
            case NOT_IN -> new Not(buildIn(descriptor, colRef, args));
            case TRUE -> Comparison.eq(colRef, LiteralUtil.createLiteral(Boolean.TRUE));
            case FALSE -> Comparison.eq(colRef, LiteralUtil.createLiteral(Boolean.FALSE));
        };
    }

    private Predicate mapComposite(
            CompositePredicateDescriptor descriptor, PropertyMetadataProvider<?, ?> metadataProvider, Object[] args) {
        if (descriptor.children().isEmpty()) {
            return new NullPredicate();
        }

        List<Predicate> predicates = new ArrayList<>();
        for (PredicateDescriptor child : descriptor.children()) {
            Predicate p = map(child, metadataProvider, args);
            if (p instanceof NullPredicate) {
                continue;
            }
            predicates.add(p);
        }

        if (predicates.isEmpty()) {
            return new NullPredicate();
        }

        return descriptor.type() == CompositePredicateDescriptor.CompositeType.AND
                ? AndOr.and(predicates)
                : AndOr.or(predicates);
    }

    // ---- Property helpers ----

    private Predicate buildEquals(PropertyPredicateDescriptor descriptor, ColumnReference colRef, Object[] args) {
        Object value = args[descriptor.paramIndex()];
        if (descriptor.ignoreCase() && value instanceof String strValue) {
            ScalarExpression lhsLower = UnaryString.lower(colRef);
            ScalarExpression rhsLower = LiteralUtil.createLiteral(strValue.toLowerCase());
            return Comparison.eq(lhsLower, rhsLower);
        }
        return Comparison.eq(colRef, LiteralUtil.createLiteral(value));
    }

    private Predicate buildNotEquals(PropertyPredicateDescriptor descriptor, ColumnReference colRef, Object[] args) {
        Object value = args[descriptor.paramIndex()];
        if (descriptor.ignoreCase() && value instanceof String strValue) {
            ScalarExpression lhsLower = UnaryString.lower(colRef);
            ScalarExpression rhsLower = LiteralUtil.createLiteral(strValue.toLowerCase());
            return Comparison.ne(lhsLower, rhsLower);
        }
        return Comparison.ne(colRef, LiteralUtil.createLiteral(value));
    }

    private Predicate buildBetween(PropertyPredicateDescriptor descriptor, ColumnReference colRef, Object[] args) {
        ScalarExpression start = LiteralUtil.createLiteral(args[descriptor.paramIndex()]);
        ScalarExpression end = LiteralUtil.createLiteral(args[descriptor.paramIndex() + 1]);
        return new Between(colRef, start, end);
    }

    private Like buildLike(
            PropertyPredicateDescriptor descriptor, ColumnReference colRef, Object[] args, String variant) {
        Object rawValue = args[descriptor.paramIndex()];
        String value = rawValue != null ? rawValue.toString() : "";
        String pattern = applyLikeWildcards(value, variant);

        if (descriptor.ignoreCase()) {
            return new Like(UnaryString.lower(colRef), pattern.toLowerCase());
        }
        return new Like(colRef, pattern);
    }

    private static String applyLikeWildcards(String value, String variant) {
        return switch (variant) {
            case "starting" -> value + "%";
            case "ending" -> "%" + value;
            case "containing" -> "%" + value + "%";
            default -> value;
        };
    }

    private Predicate buildIn(PropertyPredicateDescriptor descriptor, ColumnReference colRef, Object[] args) {
        Object rawArg = args[descriptor.paramIndex()];
        List<ValueExpression> values = new ArrayList<>();

        if (rawArg instanceof Collection<?> coll) {
            for (Object item : coll) {
                values.add(LiteralUtil.createLiteral(item));
            }
        } else if (rawArg instanceof Iterable<?> iter) {
            for (Object item : iter) {
                values.add(LiteralUtil.createLiteral(item));
            }
        } else if (rawArg != null && rawArg.getClass().isArray()) {
            for (Object item : (Object[]) rawArg) {
                values.add(LiteralUtil.createLiteral(item));
            }
        } else if (rawArg != null) {
            values.add(LiteralUtil.createLiteral(rawArg));
        }

        return new In(colRef, values);
    }
}
