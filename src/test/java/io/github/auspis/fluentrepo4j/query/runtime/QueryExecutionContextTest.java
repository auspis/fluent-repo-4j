package io.github.auspis.fluentrepo4j.query.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper;
import io.github.auspis.fluentsql4j.dsl.StatementBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.support.SQLExceptionTranslator;

class QueryExecutionContextTest {

    private FluentConnectionProvider connectionProvider;
    private SQLExceptionTranslator exceptionTranslator;
    private QueryExecutionResources<Object> ctx;
    private Connection connection;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        connectionProvider = mock(FluentConnectionProvider.class);
        FluentEntityRowMapper<Object> rowMapper = mock(FluentEntityRowMapper.class);
        exceptionTranslator = mock(SQLExceptionTranslator.class);
        ctx = new QueryExecutionResources<>(connectionProvider, rowMapper, exceptionTranslator);

        connection = mock(Connection.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
    }

    @Test
    void rowMapper_returns_configured_mapper() {
        assertThat(ctx.rowMapper()).isNotNull();
    }

    @Nested
    class ExecuteWithConnection {

        @Test
        void acquires_and_releases_connection() throws SQLException {
            StatementBuilder builder = mock(StatementBuilder.class);
            PreparedStatement ps = mock(PreparedStatement.class);
            when(builder.build(connection)).thenReturn(ps);

            ctx.executeWithConnection(builder, stmt -> "result", "test");

            verify(connectionProvider).getConnection();
            verify(connectionProvider).releaseConnection(connection);
        }

        @Test
        void returns_operation_result() throws SQLException {
            StatementBuilder builder = mock(StatementBuilder.class);
            PreparedStatement ps = mock(PreparedStatement.class);
            when(builder.build(connection)).thenReturn(ps);

            String result = ctx.executeWithConnection(builder, stmt -> "expected", "test");

            assertThat(result).isEqualTo("expected");
        }

        @Test
        void passes_prepared_statement_to_operation() throws SQLException {
            StatementBuilder builder = mock(StatementBuilder.class);
            PreparedStatement ps = mock(PreparedStatement.class);
            when(builder.build(connection)).thenReturn(ps);

            PreparedStatement[] captured = new PreparedStatement[1];
            ctx.executeWithConnection(
                    builder,
                    stmt -> {
                        captured[0] = stmt;
                        return null;
                    },
                    "test");

            assertThat(captured[0]).isSameAs(ps);
        }

        @Test
        void releases_connection_on_sql_exception_from_build() throws SQLException {
            StatementBuilder builder = mock(StatementBuilder.class);
            when(builder.build(connection)).thenThrow(new SQLException("build failed"));

            DataAccessException translated = new BadSqlGrammarException("task", "sql", new SQLException());
            when(exceptionTranslator.translate(any(), any(), any())).thenReturn(translated);

            assertThatThrownBy(() -> ctx.executeWithConnection(builder, stmt -> null, "test"))
                    .isInstanceOf(DataAccessException.class);

            verify(connectionProvider).releaseConnection(connection);
        }

        @Test
        void releases_connection_on_sql_exception_from_operation() throws SQLException {
            StatementBuilder builder = mock(StatementBuilder.class);
            PreparedStatement ps = mock(PreparedStatement.class);
            when(builder.build(connection)).thenReturn(ps);

            SQLException sqlException = new SQLException("op failed");
            DataAccessException translated = new BadSqlGrammarException("task", "sql", sqlException);
            when(exceptionTranslator.translate(any(), any(), any())).thenReturn(translated);

            assertThatThrownBy(() -> ctx.executeWithConnection(
                            builder,
                            stmt -> {
                                throw sqlException;
                            },
                            "test"))
                    .isSameAs(translated);

            verify(connectionProvider).releaseConnection(connection);
        }

        @Test
        void translates_sql_exception_with_task_name() throws SQLException {
            StatementBuilder builder = mock(StatementBuilder.class);
            SQLException sqlException = new SQLException("bad query");
            when(builder.build(connection)).thenThrow(sqlException);

            DataAccessException translated = new BadSqlGrammarException("task", "sql", sqlException);
            when(exceptionTranslator.translate(eq("FluentRepositoryQuery.myTask"), eq("bad query"), eq(sqlException)))
                    .thenReturn(translated);

            assertThatThrownBy(() -> ctx.executeWithConnection(builder, stmt -> null, "myTask"))
                    .isSameAs(translated);
        }

        @Test
        void wraps_in_uncategorized_when_translator_returns_null() throws SQLException {
            StatementBuilder builder = mock(StatementBuilder.class);
            when(builder.build(connection)).thenThrow(new SQLException("unknown"));
            when(exceptionTranslator.translate(any(), any(), any())).thenReturn(null);

            assertThatThrownBy(() -> ctx.executeWithConnection(builder, stmt -> null, "test"))
                    .isInstanceOf(UncategorizedSQLException.class);
        }
    }
}
