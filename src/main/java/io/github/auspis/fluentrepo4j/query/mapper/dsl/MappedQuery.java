package io.github.auspis.fluentrepo4j.query.mapper.dsl;

import io.github.auspis.fluentrepo4j.query.OrderByClause;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentsql4j.dsl.delete.DeleteBuilder;
import io.github.auspis.fluentsql4j.dsl.select.OrderByBuilder;
import io.github.auspis.fluentsql4j.dsl.select.SelectBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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
     * @param selectBuilder the configured select builder (WHERE clause applied; no ORDER BY or FETCH yet)
     * @param orderBy       resolved order-by clauses; may be empty
     * @param descriptor    the original descriptor (needed for maxResults / pageable indices)
     * @param args          the method arguments (needed for Pageable-based fetch/offset)
     */
    record SelectResult(
            SelectBuilder selectBuilder, List<OrderByClause> orderBy, QueryDescriptor descriptor, Object[] args)
            implements MappedQuery {

        /**
         * Builds and returns a {@link PreparedStatement} for the SELECT query,
         * applying ORDER BY and FETCH/OFFSET as needed.
         */
        public PreparedStatement buildStatement(Connection conn) throws SQLException {
            Pageable pageable = extractPageable();
            Integer maxResults = descriptor.maxResults();

            if (orderBy.isEmpty()) {
                SelectBuilder builder = selectBuilder;
                if (pageable != null && pageable.isPaged()) {
                    builder = builder.fetch(pageable.getPageSize()).offset(pageable.getOffset());
                } else if (maxResults != null) {
                    builder = builder.fetch(maxResults);
                }
                return builder.build(conn);
            }

            // With ORDER BY: use OrderByBuilder (which has public fetch/offset/build)
            OrderByBuilder orderByBuilder = selectBuilder.orderBy();
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
            if (descriptor.pageableParamIndex() >= 0 && args != null && args.length > descriptor.pageableParamIndex()) {
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
    // TODO: check  tests for this scenario, this seems to be useless
    record DeleteResult(DeleteBuilder delete) implements MappedQuery {}
}
