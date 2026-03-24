package io.github.auspis.fluentrepo4j.query.criterion;

import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentsql4j.ast.core.expression.ValueExpression;
import io.github.auspis.fluentsql4j.ast.core.expression.function.string.UnaryString;
import io.github.auspis.fluentsql4j.ast.core.expression.scalar.ColumnReference;
import io.github.auspis.fluentsql4j.ast.core.expression.scalar.ScalarExpression;
import io.github.auspis.fluentsql4j.ast.core.predicate.Between;
import io.github.auspis.fluentsql4j.ast.core.predicate.Comparison;
import io.github.auspis.fluentsql4j.ast.core.predicate.In;
import io.github.auspis.fluentsql4j.ast.core.predicate.IsNotNull;
import io.github.auspis.fluentsql4j.ast.core.predicate.IsNull;
import io.github.auspis.fluentsql4j.ast.core.predicate.Like;
import io.github.auspis.fluentsql4j.ast.core.predicate.Not;
import io.github.auspis.fluentsql4j.ast.core.predicate.Predicate;
import io.github.auspis.fluentsql4j.dsl.util.LiteralUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * A leaf criterion that compares a single entity property against one or more
 * method arguments.
 */
public record PropertyCriterion(
        String propertyPath,
        CriterionOperator operator,
        boolean ignoreCase,
        boolean negated,
        int paramIndex,
        int paramCount)
        implements Criterion {

    @Override
    public Predicate toPredicate(PropertyMetadataProvider<?, ?> metadataProvider, Object[] args) {
        String column = metadataProvider.resolveColumn(propertyPath());
        ColumnReference colRef = ColumnReference.of(null, column);

        Predicate predicate =
                switch (operator()) {
                    case EQUALS -> buildEquals(colRef, args);
                    case NOT_EQUALS -> buildNotEquals(colRef, args);
                    case LESS_THAN -> Comparison.lt(colRef, LiteralUtil.createLiteral(args[paramIndex()]));
                    case LESS_THAN_EQUAL -> Comparison.lte(colRef, LiteralUtil.createLiteral(args[paramIndex()]));
                    case GREATER_THAN -> Comparison.gt(colRef, LiteralUtil.createLiteral(args[paramIndex()]));
                    case GREATER_THAN_EQUAL -> Comparison.gte(colRef, LiteralUtil.createLiteral(args[paramIndex()]));
                    case BETWEEN -> buildBetween(colRef, args);
                    case IS_NULL -> new IsNull(colRef);
                    case IS_NOT_NULL -> new IsNotNull(colRef);
                    case LIKE -> buildLike(colRef, args, "like");
                    case NOT_LIKE -> new Not(buildLike(colRef, args, "like"));
                    case STARTING_WITH -> buildLike(colRef, args, "starting");
                    case ENDING_WITH -> buildLike(colRef, args, "ending");
                    case CONTAINING -> buildLike(colRef, args, "containing");
                    case NOT_CONTAINING -> new Not(buildLike(colRef, args, "containing"));
                    case IN -> buildIn(colRef, args);
                    case NOT_IN -> new Not(buildIn(colRef, args));
                    case TRUE -> Comparison.eq(colRef, LiteralUtil.createLiteral(Boolean.TRUE));
                    case FALSE -> Comparison.eq(colRef, LiteralUtil.createLiteral(Boolean.FALSE));
                };

        return negated() ? new Not(predicate) : predicate;
    }

    private Predicate buildEquals(ColumnReference colRef, Object[] args) {
        Object value = args[paramIndex()];
        if (ignoreCase() && value instanceof String strValue) {
            ScalarExpression lhsLower = UnaryString.lower(colRef);
            ScalarExpression rhsLower = LiteralUtil.createLiteral(strValue.toLowerCase());
            return Comparison.eq(lhsLower, rhsLower);
        }
        return Comparison.eq(colRef, LiteralUtil.createLiteral(value));
    }

    private Predicate buildNotEquals(ColumnReference colRef, Object[] args) {
        Object value = args[paramIndex()];
        if (ignoreCase() && value instanceof String strValue) {
            ScalarExpression lhsLower = UnaryString.lower(colRef);
            ScalarExpression rhsLower = LiteralUtil.createLiteral(strValue.toLowerCase());
            return Comparison.ne(lhsLower, rhsLower);
        }
        return Comparison.ne(colRef, LiteralUtil.createLiteral(value));
    }

    private Predicate buildBetween(ColumnReference colRef, Object[] args) {
        ScalarExpression start = LiteralUtil.createLiteral(args[paramIndex()]);
        ScalarExpression end = LiteralUtil.createLiteral(args[paramIndex() + 1]);
        return new Between(colRef, start, end);
    }

    private Like buildLike(ColumnReference colRef, Object[] args, String variant) {
        Object rawValue = args[paramIndex()];
        String value = rawValue != null ? rawValue.toString() : "";
        String pattern = applyLikeWildcards(value, variant);

        if (ignoreCase()) {
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

    @SuppressWarnings("unchecked")
    private Predicate buildIn(ColumnReference colRef, Object[] args) {
        Object rawArg = args[paramIndex()];
        List<ValueExpression> values = new ArrayList<>();

        if (rawArg instanceof java.util.Collection<?> coll) {
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
