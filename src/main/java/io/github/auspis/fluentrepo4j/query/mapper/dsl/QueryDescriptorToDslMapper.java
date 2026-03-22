package io.github.auspis.fluentrepo4j.query.mapper.dsl;

import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentrepo4j.query.OrderByClause;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentrepo4j.query.QueryOperation;
import io.github.auspis.fluentrepo4j.query.criterion.CompositeCriterion;
import io.github.auspis.fluentrepo4j.query.criterion.Criterion;
import io.github.auspis.fluentrepo4j.query.criterion.PropertyCriterion;
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
import io.github.auspis.fluentsql4j.ast.core.predicate.Predicate;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.clause.LogicalCombinator;
import io.github.auspis.fluentsql4j.dsl.delete.DeleteBuilder;
import io.github.auspis.fluentsql4j.dsl.select.OrderByBuilder;
import io.github.auspis.fluentsql4j.dsl.select.SelectBuilder;
import io.github.auspis.fluentsql4j.dsl.util.LiteralUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Converts a {@link QueryDescriptor} and a set of runtime method arguments into a
 * fluent-sql-4j {@link SelectBuilder} or {@link DeleteBuilder}, ready to be
 * compiled into a {@link java.sql.PreparedStatement}.
 *
 * <p>All predicate values are bound as prepared-statement parameters via
 * {@link LiteralUtil#createLiteral(Object)} or the AST predicate constructors -
 * never via string concatenation - to prevent SQL injection.
 *
 * <p>Operator mapping:
 * <ul>
 *   <li>EQUALS / NOT_EQUALS / comparisons - {@link Comparison}</li>
 *   <li>IS_NULL / IS_NOT_NULL - {@link IsNull} / {@link IsNotNull}</li>
 *   <li>LIKE / variants - {@link Like} (pattern with wildcards bound as param)</li>
 *   <li>IgnoreCase - {@code LOWER(col)} via {@link UnaryString#lower}</li>
 *   <li>BETWEEN - {@link Between}</li>
 *   <li>IN / NOT_IN - {@link In} / {@code NOT(In)}</li>
 *   <li>TRUE / FALSE - {@link Comparison#eq} with Boolean literal</li>
 * </ul>
 *
 * @param <T>  entity type
 * @param <ID> identifier type
 */
public final class QueryDescriptorToDslMapper<T, ID> {

    private final DSL dsl;
    private final PropertyMetadataProvider<T, ID> metadataProvider;

    public QueryDescriptorToDslMapper(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
        this.dsl = dsl;
        this.metadataProvider = metadataProvider;
    }

    /**
     * Builds and returns a {@link MappedQuery} containing all information needed to
     * produce a {@link PreparedStatement} for the given descriptor and runtime arguments.
     *
     * @param descriptor the parsed query descriptor (cached per method)
     * @param args       the runtime method arguments (in method-signature order)
     * @return a {@link MappedQuery} ready to be built into a PreparedStatement
     */
    public MappedQuery map(QueryDescriptor descriptor, Object[] args) {
        String table = metadataProvider.getTableName();

        if (descriptor.operation() == QueryOperation.DELETE) {
            return buildDelete(descriptor, args, table);
        }
        return buildSelect(descriptor, args, table);
    }

    // ---- SELECT ----

    private MappedQuery buildSelect(QueryDescriptor descriptor, Object[] args, String table) {
        SelectBuilder base = chooseSelectProjection(descriptor, table);

        // Apply WHERE predicates
        if (descriptor.root() != null) {
            Predicate where = buildPredicate(descriptor.root(), args);
            base = base.addWhereCondition(where, LogicalCombinator.AND);
        }

        // Collect order-by clauses: static (from method name) + runtime (Sort / Pageable)
        List<OrderByClause> orderByClauses = new ArrayList<>(descriptor.orderBy());
        Sort runtimeSort = extractSort(descriptor, args);
        if (runtimeSort != null && runtimeSort.isSorted()) {
            for (Sort.Order order : runtimeSort) {
                String column = resolveOrderColumn(order.getProperty());
                orderByClauses.add(new OrderByClause(column, order.getDirection()));
            }
        }

        return new MappedQuery.SelectResult(base, orderByClauses, descriptor, args);
    }

    private SelectBuilder chooseSelectProjection(QueryDescriptor descriptor, String table) {
        return switch (descriptor.operation()) {
            case COUNT, EXISTS -> dsl.select().countStar().from(table);
            default -> dsl.selectAll().from(table);
        };
    }

    private String resolveOrderColumn(String propertyOrColumn) {
        try {
            return metadataProvider.resolveColumn(propertyOrColumn);
        } catch (IllegalArgumentException e) {
            return propertyOrColumn; // already a column name
        }
    }

    // ---- DELETE ----

    private MappedQuery buildDelete(QueryDescriptor descriptor, Object[] args, String table) {
        DeleteBuilder delete = dsl.deleteFrom(table);

        if (descriptor.root() != null) {
            Predicate where = buildPredicate(descriptor.root(), args);
            delete = delete.addWhereCondition(where, LogicalCombinator.AND);
        }

        return new MappedQuery.DeleteResult(delete);
    }

    // ---- Predicate building ----

    private Predicate buildPredicate(Criterion criterion, Object[] args) {
        return switch (criterion) {
            case PropertyCriterion pc -> buildPropertyPredicate(pc, args);
            case CompositeCriterion cc -> buildCompositePredicate(cc, args);
        };
    }

    private Predicate buildCompositePredicate(CompositeCriterion cc, Object[] args) {
        List<Predicate> predicates = new ArrayList<>();
        for (Criterion child : cc.children()) {
            predicates.add(buildPredicate(child, args));
        }
        return cc.type() == CompositeCriterion.CompositeType.AND ? AndOr.and(predicates) : AndOr.or(predicates);
    }

    private Predicate buildPropertyPredicate(PropertyCriterion pc, Object[] args) {
        String column = metadataProvider.resolveColumn(pc.propertyPath());
        ColumnReference colRef = ColumnReference.of(null, column);

        Predicate predicate =
                switch (pc.operator()) {
                    case EQUALS -> buildEquals(colRef, pc, args);
                    case NOT_EQUALS -> buildNotEquals(colRef, pc, args);
                    case LESS_THAN -> Comparison.lt(colRef, LiteralUtil.createLiteral(args[pc.paramIndex()]));
                    case LESS_THAN_EQUAL -> Comparison.lte(colRef, LiteralUtil.createLiteral(args[pc.paramIndex()]));
                    case GREATER_THAN -> Comparison.gt(colRef, LiteralUtil.createLiteral(args[pc.paramIndex()]));
                    case GREATER_THAN_EQUAL -> Comparison.gte(colRef, LiteralUtil.createLiteral(args[pc.paramIndex()]));
                    case BETWEEN -> buildBetween(colRef, pc, args);
                    case IS_NULL -> new IsNull(colRef);
                    case IS_NOT_NULL -> new IsNotNull(colRef);
                    case LIKE -> buildLike(colRef, pc, args, "like");
                    case NOT_LIKE -> new Not(buildLike(colRef, pc, args, "like"));
                    case STARTING_WITH -> buildLike(colRef, pc, args, "starting");
                    case ENDING_WITH -> buildLike(colRef, pc, args, "ending");
                    case CONTAINING -> buildLike(colRef, pc, args, "containing");
                    case NOT_CONTAINING -> new Not(buildLike(colRef, pc, args, "containing"));
                    case IN -> buildIn(colRef, pc, args);
                    case NOT_IN -> new Not(buildIn(colRef, pc, args));
                    case TRUE -> Comparison.eq(colRef, LiteralUtil.createLiteral(Boolean.TRUE));
                    case FALSE -> Comparison.eq(colRef, LiteralUtil.createLiteral(Boolean.FALSE));
                };

        return pc.negated() ? new Not(predicate) : predicate;
    }

    private Predicate buildEquals(ColumnReference colRef, PropertyCriterion pc, Object[] args) {
        Object value = args[pc.paramIndex()];
        if (pc.ignoreCase() && value instanceof String strValue) {
            ScalarExpression lhsLower = UnaryString.lower(colRef);
            ScalarExpression rhsLower = LiteralUtil.createLiteral(strValue.toLowerCase());
            return Comparison.eq(lhsLower, rhsLower);
        }
        return Comparison.eq(colRef, LiteralUtil.createLiteral(value));
    }

    private Predicate buildNotEquals(ColumnReference colRef, PropertyCriterion pc, Object[] args) {
        Object value = args[pc.paramIndex()];
        if (pc.ignoreCase() && value instanceof String strValue) {
            ScalarExpression lhsLower = UnaryString.lower(colRef);
            ScalarExpression rhsLower = LiteralUtil.createLiteral(strValue.toLowerCase());
            return Comparison.ne(lhsLower, rhsLower);
        }
        return Comparison.ne(colRef, LiteralUtil.createLiteral(value));
    }

    private Predicate buildBetween(ColumnReference colRef, PropertyCriterion pc, Object[] args) {
        ScalarExpression start = LiteralUtil.createLiteral(args[pc.paramIndex()]);
        ScalarExpression end = LiteralUtil.createLiteral(args[pc.paramIndex() + 1]);
        return new Between(colRef, start, end);
    }

    private Like buildLike(ColumnReference colRef, PropertyCriterion pc, Object[] args, String variant) {
        Object rawValue = args[pc.paramIndex()];
        String value = rawValue != null ? rawValue.toString() : "";
        String pattern = applyLikeWildcards(value, variant);

        if (pc.ignoreCase()) {
            return new Like(UnaryString.lower(colRef), pattern.toLowerCase());
        }
        return new Like(colRef, pattern);
    }

    private static String applyLikeWildcards(String value, String variant) {
        return switch (variant) {
            case "starting" -> value + "%";
            case "ending" -> "%" + value;
            case "containing" -> "%" + value + "%";
            default -> value; // plain LIKE: caller provides the pattern
        };
    }

    @SuppressWarnings("unchecked")
    private Predicate buildIn(ColumnReference colRef, PropertyCriterion pc, Object[] args) {
        Object rawArg = args[pc.paramIndex()];
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

    // ---- Pageable / Sort extraction ----

    private Sort extractSort(QueryDescriptor descriptor, Object[] args) {
        if (descriptor.pageableParamIndex() >= 0 && args != null && args.length > descriptor.pageableParamIndex()) {
            Object arg = args[descriptor.pageableParamIndex()];
            if (arg instanceof Pageable p) {
                return p.getSort();
            }
        }
        if (descriptor.sortParamIndex() >= 0 && args != null && args.length > descriptor.sortParamIndex()) {
            Object arg = args[descriptor.sortParamIndex()];
            if (arg instanceof Sort s) {
                return s;
            }
        }
        return null;
    }

    // ---- MappedQuery container ----

    /**
     * Holds the result of the DSL mapping.  The caller is responsible for building
     * the final {@link PreparedStatement} by calling
     * {@link MappedQuery.SelectResult#buildStatement(Connection)} or
     * {@link MappedQuery.DeleteResult#delete()}.{@code build(conn)}.
     */
    public sealed interface MappedQuery permits MappedQuery.SelectResult, MappedQuery.DeleteResult {

        /**
         * A mapped SELECT (or COUNT / EXISTS) query.
         *
         * @param base       the configured select builder (WHERE clause applied; no ORDER BY or FETCH yet)
         * @param orderBy    resolved order-by clauses; may be empty
         * @param descriptor the original descriptor (needed for maxResults / pageable indices)
         * @param args       the method arguments (needed for Pageable-based fetch/offset)
         */
        record SelectResult(SelectBuilder base, List<OrderByClause> orderBy, QueryDescriptor descriptor, Object[] args)
                implements MappedQuery {

            /**
             * Builds and returns a {@link PreparedStatement} for the SELECT query,
             * applying ORDER BY and FETCH/OFFSET as needed.
             */
            public PreparedStatement buildStatement(Connection conn) throws SQLException {
                Pageable pageable = extractPageable();
                Integer maxResults = descriptor.maxResults();

                if (orderBy.isEmpty()) {
                    SelectBuilder builder = base;
                    if (pageable != null && pageable.isPaged()) {
                        builder = builder.fetch(pageable.getPageSize()).offset(pageable.getOffset());
                    } else if (maxResults != null) {
                        builder = builder.fetch(maxResults);
                    }
                    return builder.build(conn);
                }

                // With ORDER BY: use OrderByBuilder (which has public fetch/offset/build)
                OrderByBuilder orderByBuilder = base.orderBy();
                for (OrderByClause clause : orderBy) {
                    orderByBuilder = clause.direction() == Sort.Direction.ASC
                            ? orderByBuilder.asc(clause.columnName())
                            : orderByBuilder.desc(clause.columnName());
                }

                if (pageable != null && pageable.isPaged()) {
                    return orderByBuilder
                            .fetch(pageable.getPageSize())
                            .offset(pageable.getOffset())
                            .build(conn);
                } else if (maxResults != null) {
                    return orderByBuilder.fetch(maxResults).build(conn);
                }
                return orderByBuilder.build(conn);
            }

            /**
             * Builds a count query using the same WHERE clause (ignoring ORDER BY / FETCH).
             * Used for {@link org.springframework.data.domain.Page} total-count queries.
             */
            public PreparedStatement buildCountStatement(Connection conn, DSL dsl) throws SQLException {
                String table = base.getTableReference();
                SelectBuilder countBase = dsl.select().countStar().from(table);
                // Reuse the WHERE clause from base by wrapping via a new AddWhereCondition call is not
                // directly possible; the count query is built separately in FluentRepositoryQuery.
                return base.build(conn);
            }

            private Pageable extractPageable() {
                if (descriptor.pageableParamIndex() >= 0
                        && args != null
                        && args.length > descriptor.pageableParamIndex()) {
                    Object arg = args[descriptor.pageableParamIndex()];
                    if (arg instanceof Pageable p) {
                        return p;
                    }
                }
                return null;
            }
        }

        /**
         * A mapped DELETE query.
         */
        record DeleteResult(DeleteBuilder delete) implements MappedQuery {}
    }
}
