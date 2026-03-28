package io.github.auspis.fluentrepo4j.query.runtime;

import io.github.auspis.fluentrepo4j.query.runtime.ExecutableQuery.CountQuery;
import io.github.auspis.fluentrepo4j.query.runtime.ExecutableQuery.DeleteQuery;
import io.github.auspis.fluentrepo4j.query.runtime.ExecutableQuery.EntitySelectQuery;
import io.github.auspis.fluentrepo4j.query.runtime.ExecutableQuery.ExistsQuery;
import io.github.auspis.fluentsql4j.dsl.delete.DeleteBuilder;
import io.github.auspis.fluentsql4j.dsl.select.SelectBuilder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * A fully-configured query that can execute itself given a
 * {@link QueryExecutionResources}. Each variant knows how to run its specific
 * SQL operation and return the appropriate raw result.
 *
 * <p>Produced by
 * {@link io.github.auspis.fluentrepo4j.query.mapper.dsl.QueryDescriptorToDslMapper}
 * and consumed by {@link FluentRepositoryQuery}.
 *
 * @param <T> entity type
 */
public sealed interface ExecutableQuery<T> permits CountQuery, ExistsQuery, EntitySelectQuery, DeleteQuery {

    /**
     * Executes this query against the database using the given execution context.
     *
     * @param ctx the runtime execution context (connection, row mapper, etc.)
     * @return the raw result: {@code Long} for count, {@code Boolean} for exists,
     *         {@code List<T>} for entity select, {@code Integer} for delete
     */
    Object execute(QueryExecutionResources<T> ctx);

    /**
     * {@code SELECT COUNT(*)} query returning a {@code long}.
     */
    record CountQuery<T>(SelectBuilder statementBuilder) implements ExecutableQuery<T> {

        @Override
        public Object execute(QueryExecutionResources<T> ctx) {
            return ctx.executeWithConnection(
                    statementBuilder,
                    (PreparedStatement ps) -> {
                        try (ps;
                                ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            return rs.getLong(1);
                        }
                    },
                    "count");
        }
    }

    /**
     * {@code SELECT COUNT(*) > 0} query returning a {@code boolean}.
     */
    record ExistsQuery<T>(SelectBuilder statementBuilder) implements ExecutableQuery<T> {

        @Override
        public Object execute(QueryExecutionResources<T> ctx) {
            long count = (long) new CountQuery<T>(statementBuilder).execute(ctx);
            return count > 0;
        }
    }

    /**
     * {@code SELECT *} (or column-list) query returning a {@code List<T>}.
     */
    record EntitySelectQuery<T>(SelectBuilder statementBuilder) implements ExecutableQuery<T> {

        @Override
        public Object execute(QueryExecutionResources<T> ctx) {
            return ctx.executeWithConnection(
                    statementBuilder,
                    (PreparedStatement ps) -> {
                        try (ps;
                                ResultSet rs = ps.executeQuery()) {
                            List<T> results = new ArrayList<>();
                            int rowNum = 0;
                            while (rs.next()) {
                                results.add(ctx.rowMapper().mapRow(rs, rowNum++));
                            }
                            return results;
                        }
                    },
                    "find");
        }
    }

    /**
     * {@code DELETE} query returning the number of affected rows as {@code int}.
     */
    record DeleteQuery<T>(DeleteBuilder statementBuilder) implements ExecutableQuery<T> {

        @Override
        public Object execute(QueryExecutionResources<T> ctx) {
            return ctx.executeWithConnection(
                    statementBuilder,
                    (PreparedStatement ps) -> {
                        try (ps) {
                            return ps.executeUpdate();
                        }
                    },
                    "delete");
        }
    }
}
