package io.github.auspis.fluentrepo4j.parse;

import io.github.auspis.fluentrepo4j.query.OrderByClause;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentrepo4j.query.QueryOperation;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.CompositePredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.CompositePredicateDescriptor.CompositeType;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.NullPredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.PropertyPredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptorOperator;
import io.github.auspis.fluentsql4j.ast.dql.clause.Sorting;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.DefaultParameters;
import org.springframework.data.repository.query.ParametersSource;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * Converts a Spring Data {@link PartTre
 *
 * import java.lang.reflect.Method;
 * import java.util.ArrayList;
 * import java.util.List;
 *
 * import org.springframework.data.domain.Sort;
 * import org.springframework.data.repository.quethub.auspis.fluentrepo4j.query.QueryDescriptor;
 * import io.github.auspis.fluentrepo4j
 * import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.CompositePredicateDescriptor;
 * import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.CompositePredicateDescriptor.CompositeType;
 * import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.NullPredicateDescriptor;
 * import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.PropertyPredicateDescriptor;.query.QueryOperation;
 * import io.github.auspis.fluentrepo4j.query.predicatedescriptor.Prediciptor.CompositePredicateDescriptor;
 * import io.github.auspis.e} (derived from a repository method name)
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

        int pageableParamIndex = parameters.hasPageableParameter() ? parameters.getPageableIndex() : -1;
        int sortParamIndex = parameters.hasSortParameter() ? parameters.getSortIndex() : -1;

        return new QueryDescriptor(
                operation(tree),
                tree.isDistinct(),
                tree.getMaxResults(),
                predicateDescriptor(tree, pageableParamIndex, sortParamIndex),
                oOrderBy(tree.getSort()),
                pageableParamIndex,
                sortParamIndex);
    }

    // ---- Private helpers ----

    private static QueryOperation operation(PartTree tree) {
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

    private static PredicateDescriptor predicateDescriptor(PartTree tree, int pageableParamIndex, int sortParamIndex) {
        if (!tree.hasPredicate()) {
            return NullPredicateDescriptor.INSTANCE;
        }

        List<PredicateDescriptor> orParts = new ArrayList<>();
        int nextParamIndex = 0;

        for (PartTree.OrPart orPart : tree) {
            List<PredicateDescriptor> andParts = new ArrayList<>();

            for (Part part : orPart) {
                // Advance past special parameter slots
                while (nextParamIndex == pageableParamIndex || nextParamIndex == sortParamIndex) {
                    nextParamIndex++;
                }

                int paramCount = part.getNumberOfArguments();

                andParts.add(new PropertyPredicateDescriptor(
                        part.getProperty().getSegment(),
                        mapOperator(part.getType()),
                        isIgnoreCase(part),
                        nextParamIndex,
                        paramCount));

                nextParamIndex += paramCount;
            }

            orParts.add(
                    andParts.size() == 1
                            ? andParts.get(0)
                            : new CompositePredicateDescriptor(CompositeType.AND, andParts));
        }

        return switch (orParts.size()) {
            case 0 -> NullPredicateDescriptor.INSTANCE;
            case 1 -> orParts.get(0);
            default -> new CompositePredicateDescriptor(CompositeType.OR, orParts);
        };
    }

    private static List<OrderByClause> oOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return List.of();
        }
        List<OrderByClause> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            Sorting.SortOrder direction = order.isAscending() ? Sorting.SortOrder.ASC : Sorting.SortOrder.DESC;
            clauses.add(new OrderByClause(order.getProperty(), direction));
        }
        return clauses;
    }

    private static PredicateDescriptorOperator mapOperator(Part.Type type) {
        return switch (type) {
            case SIMPLE_PROPERTY -> PredicateDescriptorOperator.EQUALS;
            case NEGATING_SIMPLE_PROPERTY -> PredicateDescriptorOperator.NOT_EQUALS;
            case LESS_THAN, BEFORE -> PredicateDescriptorOperator.LESS_THAN;
            case LESS_THAN_EQUAL -> PredicateDescriptorOperator.LESS_THAN_EQUAL;
            case GREATER_THAN, AFTER -> PredicateDescriptorOperator.GREATER_THAN;
            case GREATER_THAN_EQUAL -> PredicateDescriptorOperator.GREATER_THAN_EQUAL;
            case BETWEEN -> PredicateDescriptorOperator.BETWEEN;
            case IS_NULL -> PredicateDescriptorOperator.IS_NULL;
            case IS_NOT_NULL -> PredicateDescriptorOperator.IS_NOT_NULL;
            case LIKE -> PredicateDescriptorOperator.LIKE;
            case NOT_LIKE -> PredicateDescriptorOperator.NOT_LIKE;
            case STARTING_WITH -> PredicateDescriptorOperator.STARTING_WITH;
            case ENDING_WITH -> PredicateDescriptorOperator.ENDING_WITH;
            case CONTAINING -> PredicateDescriptorOperator.CONTAINING;
            case NOT_CONTAINING -> PredicateDescriptorOperator.NOT_CONTAINING;
            case IN -> PredicateDescriptorOperator.IN;
            case NOT_IN -> PredicateDescriptorOperator.NOT_IN;
            case TRUE -> PredicateDescriptorOperator.TRUE;
            case FALSE -> PredicateDescriptorOperator.FALSE;
            case IS_EMPTY, IS_NOT_EMPTY, NEAR, WITHIN, REGEX, EXISTS ->
                throw new UnsupportedOperationException(
                        "Operator " + type + " is not supported by fluent-repo-4j dynamic queries.");
        };
    }

    private static boolean isIgnoreCase(Part part) {
        return part.shouldIgnoreCase() != Part.IgnoreCaseType.NEVER;
    }
}
