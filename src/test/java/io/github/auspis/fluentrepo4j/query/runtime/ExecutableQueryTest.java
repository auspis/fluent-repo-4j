package io.github.auspis.fluentrepo4j.query.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper;
import io.github.auspis.fluentsql4j.dsl.delete.DeleteBuilder;
import io.github.auspis.fluentsql4j.dsl.select.SelectBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.SQLExceptionTranslator;

class ExecutableQueryTest {

    private QueryExecutionResources<Object> ctx;
    private Connection connection;
    private PreparedStatement preparedStatement;
    private ResultSet resultSet;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        FluentConnectionProvider connectionProvider = mock(FluentConnectionProvider.class);
        FluentEntityRowMapper<Object> rowMapper = mock(FluentEntityRowMapper.class);
        SQLExceptionTranslator exceptionTranslator = mock(SQLExceptionTranslator.class);
        ctx = new QueryExecutionResources<>(connectionProvider, rowMapper, exceptionTranslator);

        connection = mock(Connection.class);
        preparedStatement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);

        when(connectionProvider.getConnection()).thenReturn(connection);
    }

    @Nested
    class CountQueryTests {

        @Test
        void returns_count_from_result_set() throws SQLException {
            SelectBuilder selectBuilder = mock(SelectBuilder.class);
            when(selectBuilder.build(connection)).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getLong(1)).thenReturn(42L);

            ExecutableQuery<Object> query = new ExecutableQuery.CountQuery<>(selectBuilder);
            Object result = query.execute(ctx);

            assertThat(result).isEqualTo(42L);
        }

        @Test
        void statementBuilder_returns_select_builder() {
            SelectBuilder selectBuilder = mock(SelectBuilder.class);
            ExecutableQuery<Object> query = new ExecutableQuery.CountQuery<>(selectBuilder);

            assertThat(query.statementBuilder()).isSameAs(selectBuilder);
        }
    }

    @Nested
    class ExistsQueryTests {

        @Test
        void returns_true_when_count_greater_than_zero() throws SQLException {
            SelectBuilder selectBuilder = mock(SelectBuilder.class);
            when(selectBuilder.build(connection)).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getLong(1)).thenReturn(1L);

            ExecutableQuery<Object> query = new ExecutableQuery.ExistsQuery<>(selectBuilder);
            Object result = query.execute(ctx);

            assertThat(result).isEqualTo(true);
        }

        @Test
        void returns_false_when_count_is_zero() throws SQLException {
            SelectBuilder selectBuilder = mock(SelectBuilder.class);
            when(selectBuilder.build(connection)).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getLong(1)).thenReturn(0L);

            ExecutableQuery<Object> query = new ExecutableQuery.ExistsQuery<>(selectBuilder);
            Object result = query.execute(ctx);

            assertThat(result).isEqualTo(false);
        }

        @Test
        void statementBuilder_returns_select_builder() {
            SelectBuilder selectBuilder = mock(SelectBuilder.class);
            ExecutableQuery<Object> query = new ExecutableQuery.ExistsQuery<>(selectBuilder);

            assertThat(query.statementBuilder()).isSameAs(selectBuilder);
        }
    }

    @Nested
    class EntitySelectQueryTests {

        @SuppressWarnings("unchecked")
        @Test
        void returns_mapped_entities() throws SQLException {
            SelectBuilder selectBuilder = mock(SelectBuilder.class);
            when(selectBuilder.build(connection)).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, true, false);

            Object entity1 = new Object();
            Object entity2 = new Object();
            when(ctx.rowMapper().mapRow(resultSet, 0)).thenReturn(entity1);
            when(ctx.rowMapper().mapRow(resultSet, 1)).thenReturn(entity2);

            ExecutableQuery<Object> query = new ExecutableQuery.EntitySelectQuery<>(selectBuilder);
            Object result = query.execute(ctx);

            assertThat(result).isInstanceOf(List.class);
            List<Object> results = (List<Object>) result;
            assertThat(results).containsExactly(entity1, entity2);
        }

        @SuppressWarnings("unchecked")
        @Test
        void returns_empty_list_when_no_rows() throws SQLException {
            SelectBuilder selectBuilder = mock(SelectBuilder.class);
            when(selectBuilder.build(connection)).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            ExecutableQuery<Object> query = new ExecutableQuery.EntitySelectQuery<>(selectBuilder);
            Object result = query.execute(ctx);

            assertThat(result).isInstanceOf(List.class);
            List<Object> results = (List<Object>) result;
            assertThat(results).isEmpty();
        }

        @Test
        void statementBuilder_returns_select_builder() {
            SelectBuilder selectBuilder = mock(SelectBuilder.class);
            ExecutableQuery<Object> query = new ExecutableQuery.EntitySelectQuery<>(selectBuilder);

            assertThat(query.statementBuilder()).isSameAs(selectBuilder);
        }
    }

    @Nested
    class DeleteQueryTests {

        @Test
        void returns_affected_row_count() throws SQLException {
            DeleteBuilder deleteBuilder = mock(DeleteBuilder.class);
            when(deleteBuilder.build(connection)).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(3);

            ExecutableQuery<Object> query = new ExecutableQuery.DeleteQuery<>(deleteBuilder);
            Object result = query.execute(ctx);

            assertThat(result).isEqualTo(3);
        }

        @Test
        void statementBuilder_returns_delete_builder() {
            DeleteBuilder deleteBuilder = mock(DeleteBuilder.class);
            ExecutableQuery<Object> query = new ExecutableQuery.DeleteQuery<>(deleteBuilder);

            assertThat(query.statementBuilder()).isSameAs(deleteBuilder);
        }
    }
}
