package io.github.auspis.fluentrepo4j.query.runtime;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper;
import io.github.auspis.fluentsql4j.dsl.StatementBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.SQLExceptionTranslator;

/**
 * Provides the runtime dependencies needed to execute a mapped query:
 * connection lifecycle, row mapping, and exception translation.
 *
 * <p>The template method {@link #executeWithConnection} acquires a connection,
 * builds a {@link PreparedStatement} from the given {@link StatementBuilder},
 * applies the caller-supplied operation, and guarantees connection release and
 * Spring-consistent exception translation.
 *
 * @param <T> entity type
 */
public class QueryExecutionResources<T> {

    private final FluentConnectionProvider connectionProvider;
    private final FluentEntityRowMapper<T> rowMapper;
    private final SQLExceptionTranslator exceptionTranslator;

    public QueryExecutionResources(
            FluentConnectionProvider connectionProvider,
            FluentEntityRowMapper<T> rowMapper,
            SQLExceptionTranslator exceptionTranslator) {
        this.connectionProvider = connectionProvider;
        this.rowMapper = rowMapper;
        this.exceptionTranslator = exceptionTranslator;
    }

    public FluentEntityRowMapper<T> rowMapper() {
        return rowMapper;
    }

    /**
     * Acquires a connection, builds a {@link PreparedStatement} from the given
     * builder, applies the operation, and guarantees connection release.
     *
     * @param <R>       result type
     * @param statementBuilder   the fully-configured statement builder
     * @param operation the operation to execute on the prepared statement
     * @param taskName  a label used in exception messages
     * @return the result of the operation
     * @throws DataAccessException if a {@link SQLException} occurs
     */
    public <R> R executeWithConnection(
            StatementBuilder statementBuilder, StatementOperation<R> operation, String taskName) {
        Connection conn = connectionProvider.getConnection();
        try {
            PreparedStatement ps = statementBuilder.build(conn);
            return operation.execute(ps);
        } catch (SQLException e) {
            throw translateException(taskName, e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    private DataAccessException translateException(String task, SQLException ex) {
        DataAccessException translated =
                exceptionTranslator.translate("FluentRepositoryQuery." + task, ex.getMessage(), ex);
        return translated != null
                ? translated
                : new org.springframework.jdbc.UncategorizedSQLException("FluentRepositoryQuery." + task, null, ex);
    }

    /**
     * An operation applied to a {@link PreparedStatement} that returns a typed result.
     *
     * @param <R> result type
     */
    @FunctionalInterface
    public interface StatementOperation<R> {
        R execute(PreparedStatement ps) throws SQLException;
    }
}
