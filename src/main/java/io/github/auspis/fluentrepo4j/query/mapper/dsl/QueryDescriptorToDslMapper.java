package io.github.auspis.fluentrepo4j.query.mapper.dsl;

import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentrepo4j.query.OrderByClause;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentrepo4j.query.QueryOperation;
import io.github.auspis.fluentsql4j.ast.core.expression.function.string.UnaryString;
import io.github.auspis.fluentsql4j.ast.core.predicate.Between;
import io.github.auspis.fluentsql4j.ast.core.predicate.Comparison;
import io.github.auspis.fluentsql4j.ast.core.predicate.In;
import io.github.auspis.fluentsql4j.ast.core.predicate.IsNotNull;
import io.github.auspis.fluentsql4j.ast.core.predicate.IsNull;
import io.github.auspis.fluentsql4j.ast.core.predicate.Like;
import io.github.auspis.fluentsql4j.ast.core.predicate.NullPredicate;
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
        Predicate where = descriptor.root().toPredicate(metadataProvider, args);
        if (where != null && !(where instanceof NullPredicate)) {
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

        Predicate where = descriptor.root().toPredicate(metadataProvider, args);
        if (where != null && !(where instanceof NullPredicate)) {
            delete = delete.addWhereCondition(where, LogicalCombinator.AND);
        }

        return new MappedQuery.DeleteResult(delete);
    }

    // Predicate construction delegated to Criterion implementations via toPredicate().

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
