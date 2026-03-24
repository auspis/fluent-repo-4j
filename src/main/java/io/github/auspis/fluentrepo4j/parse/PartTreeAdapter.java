package io.github.auspis.fluentrepo4j.parse;

import io.github.auspis.fluentrepo4j.query.OrderByClause;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentrepo4j.query.QueryOperation;
import io.github.auspis.fluentrepo4j.query.criterion.CompositeCriterion;
import io.github.auspis.fluentrepo4j.query.criterion.CompositeCriterion.CompositeType;
import io.github.auspis.fluentrepo4j.query.criterion.Criterion;
import io.github.auspis.fluentrepo4j.query.criterion.CriterionOperator;
import io.github.auspis.fluentrepo4j.query.criterion.NullCriterion;
import io.github.auspis.fluentrepo4j.query.criterion.PropertyCriterion;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.DefaultParameters;
import org.springframework.data.repository.query.ParametersSource;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * Converts a Spring Data {@link PartTree} (derived from a repository method name)
 * into a neutral {@link QueryDescriptor}.
 *
 * <p>Usage:
 * <pre>{@code
 * QueryDescriptor descriptor = PartTreeAdapter.adapt(method, User.class);
 * }</pre>
 *
 * <h3>Supported operators</h3>
 * <p>All standard Spring Data {@link Part.Type} values are mapped, except the
 * geographic operators {@code NEAR} and {@code WITHIN} and {@code REGEX}
 * (which require driver-specific support).
 */
public final class PartTreeAdapter {

    private PartTreeAdapter() {}

    /**
     * Parses the given method name against {@code domainClass} and builds a
     * {@link QueryDescriptor}.
     *
     * @param method      the repository method to parse
     * @param domainClass the entity type bound to the repository
     * @return a {@link QueryDescriptor} representing the derived query
     */
    public static QueryDescriptor adapt(Method method, Class<?> domainClass) {
        PartTree tree = new PartTree(method.getName(), domainClass);
        DefaultParameters parameters = new DefaultParameters(ParametersSource.of(method));

        QueryOperation operation = resolveOperation(tree);
        boolean distinct = tree.isDistinct();
        Integer maxResults = tree.getMaxResults();

        int pageableParamIndex = parameters.hasPageableParameter() ? parameters.getPageableIndex() : -1;
        int sortParamIndex = parameters.hasSortParameter() ? parameters.getSortIndex() : -1;

        // Build criterion tree from PartTree predicates
        Criterion root = buildCriterion(tree, pageableParamIndex, sortParamIndex);

        // Build static OrderBy clauses from the method name  (PartTree.getSort())
        List<OrderByClause> orderBy = buildOrderBy(tree.getSort());

        return new QueryDescriptor(operation, distinct, maxResults, root, orderBy, pageableParamIndex, sortParamIndex);
    }

    // ---- Private helpers ----

    private static QueryOperation resolveOperation(PartTree tree) {
        if (tree.isDelete()) {
            return QueryOperation.DELETE;
        }
        if (tree.isCountProjection()) {
            return QueryOperation.COUNT;
        }
        if (tree.isExistsProjection()) {
            return QueryOperation.EXISTS;
        }
        return QueryOperation.FIND;
    }

    /**
     * Translates PartTree's OR-of-ANDs structure into a {@link CompositeCriterion}.
     * Returns {@code null} when the method has no predicate (e.g. {@code findAll}).
     */
    private static Criterion buildCriterion(PartTree tree, int pageableParamIndex, int sortParamIndex) {
        if (!tree.hasPredicate()) {
            return NullCriterion.INSTANCE;
        }

        // Calculate which indices are "special" (Pageable/Sort) so we can skip them
        // when assigning parameter indices to criteria
        List<Criterion> orParts = new ArrayList<>();
        int nextParamIndex = 0;

        for (PartTree.OrPart orPart : tree) {
            List<Criterion> andParts = new ArrayList<>();

            for (Part part : orPart) {
                // Advance past special parameter slots
                while (nextParamIndex == pageableParamIndex || nextParamIndex == sortParamIndex) {
                    nextParamIndex++;
                }

                CriterionOperator operator = mapOperator(part.getType());
                boolean ignoreCase = isIgnoreCase(part);
                boolean negated = isNegated(part);
                int paramCount = part.getNumberOfArguments();

                andParts.add(new PropertyCriterion(
                        part.getProperty().getSegment(), operator, ignoreCase, negated, nextParamIndex, paramCount));

                nextParamIndex += paramCount;
            }

            orParts.add(andParts.size() == 1 ? andParts.get(0) : new CompositeCriterion(CompositeType.AND, andParts));
        }

        if (orParts.isEmpty()) {
            return NullCriterion.INSTANCE;
        }
        return orParts.size() == 1 ? orParts.get(0) : new CompositeCriterion(CompositeType.OR, orParts);
    }

    private static List<OrderByClause> buildOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return List.of();
        }
        List<OrderByClause> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            // property name is used here; column resolution happens in the mapper
            clauses.add(new OrderByClause(order.getProperty(), order.getDirection()));
        }
        return clauses;
    }

    /**
     * Maps a Spring Data {@link Part.Type} to the neutral {@link CriterionOperator}.
     */
    private static CriterionOperator mapOperator(Part.Type type) {
        return switch (type) {
            case SIMPLE_PROPERTY -> CriterionOperator.EQUALS;
            case NEGATING_SIMPLE_PROPERTY -> CriterionOperator.NOT_EQUALS;
            case LESS_THAN, BEFORE -> CriterionOperator.LESS_THAN;
            case LESS_THAN_EQUAL -> CriterionOperator.LESS_THAN_EQUAL;
            case GREATER_THAN, AFTER -> CriterionOperator.GREATER_THAN;
            case GREATER_THAN_EQUAL -> CriterionOperator.GREATER_THAN_EQUAL;
            case BETWEEN -> CriterionOperator.BETWEEN;
            case IS_NULL -> CriterionOperator.IS_NULL;
            case IS_NOT_NULL -> CriterionOperator.IS_NOT_NULL;
            case LIKE -> CriterionOperator.LIKE;
            case NOT_LIKE -> CriterionOperator.NOT_LIKE;
            case STARTING_WITH -> CriterionOperator.STARTING_WITH;
            case ENDING_WITH -> CriterionOperator.ENDING_WITH;
            case CONTAINING -> CriterionOperator.CONTAINING;
            case NOT_CONTAINING -> CriterionOperator.NOT_CONTAINING;
            case IN -> CriterionOperator.IN;
            case NOT_IN -> CriterionOperator.NOT_IN;
            case TRUE -> CriterionOperator.TRUE;
            case FALSE -> CriterionOperator.FALSE;
            case IS_EMPTY, IS_NOT_EMPTY, NEAR, WITHIN, REGEX, EXISTS ->
                throw new UnsupportedOperationException(
                        "Operator " + type + " is not supported by fluent-repo-4j dynamic queries.");
        };
    }

    private static boolean isIgnoreCase(Part part) {
        return part.shouldIgnoreCase() != Part.IgnoreCaseType.NEVER;
    }

    /**
     * A {@link Part} is negated when its type is {@link Part.Type#NEGATING_SIMPLE_PROPERTY}
     * or one of the NOT_LIKE / NOT_CONTAINING / NOT_IN variants.  Those are
     * already modelled via distinct {@link CriterionOperator} values, so the
     * {@code negated} flag on {@link PropertyCriterion} is {@code false} for
     * them.  It is {@code true} only when the operator itself carries a {@code Not}
     * prefix that is separate from the base operator (currently none, but
     * preserved as a hook for future extensions).
     */
    private static boolean isNegated(Part part) {
        return false; // negation is fully represented by the operator (NOT_EQUALS, NOT_LIKE, etc.)
    }
}
