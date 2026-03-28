package io.github.auspis.fluentrepo4j.query.mapper.dsl;

import io.github.auspis.fluentrepo4j.query.mapper.dsl.MappedQuery.Delete;
import io.github.auspis.fluentrepo4j.query.mapper.dsl.MappedQuery.Select;
import io.github.auspis.fluentsql4j.dsl.StatementBuilder;
import io.github.auspis.fluentsql4j.dsl.delete.DeleteBuilder;
import io.github.auspis.fluentsql4j.dsl.select.SelectBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Holds the result of the DSL mapping. Both variants expose a fully-configured
 * builder and delegate {@link #buildStatement(Connection)} to it.
 *
 * <p>Strategies ({@link MappedQueryStrategy}) are responsible for applying all
 * query options (WHERE, ORDER BY, FETCH/OFFSET) before returning a result; these
 * records are plain DTOs.
 */
public sealed interface MappedQuery permits Select, Delete {

    /**
     * Returns the underlying {@link StatementBuilder} for this query result.
     * Implementations simply return the builder record component.
     */
    StatementBuilder statementBuilder();

    /**
     * Builds and returns a {@link PreparedStatement} by delegating to
     * {@link #statementBuilder()}.
     */
    default PreparedStatement buildStatement(Connection conn) throws SQLException {
        return statementBuilder().build(conn);
    }

    /**
     * A mapped SELECT (or COUNT / EXISTS) query. The {@code selectBuilder} is
     * fully configured (WHERE, ORDER BY, FETCH/OFFSET already applied).
     */
    record Select(SelectBuilder selectBuilder) implements MappedQuery {

        @Override
        public StatementBuilder statementBuilder() {
            return selectBuilder;
        }
    }

    /**
     * A mapped DELETE query. The {@code deleteBuilder} is fully configured
     * (WHERE already applied).
     */
    record Delete(DeleteBuilder deleteBuilder) implements MappedQuery {

        @Override
        public StatementBuilder statementBuilder() {
            return deleteBuilder;
        }
    }
}
