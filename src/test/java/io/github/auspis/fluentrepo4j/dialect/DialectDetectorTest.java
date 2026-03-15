package io.github.auspis.fluentrepo4j.dialect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.functional.Result;
import io.github.auspis.fluentsql4j.plugin.builtin.mysql.MysqlDialectPlugin;
import io.github.auspis.fluentsql4j.plugin.builtin.sql2016.StandardSQLDialectPlugin;
import io.github.auspis.fluentsql4j.test.util.annotation.ComponentTest;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Component test for {@link DialectDetector}.
 * Verifies dialect detection and error-handling branches
 * using mocked JDBC metadata and {@link DSLRegistry}.
 */
@ComponentTest
class DialectDetectorTest {

    private DataSource dataSource;
    private Connection connection;
    private DatabaseMetaData metaData;
    private DSLRegistry registry;
    private DSL expectedDsl;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        metaData = mock(DatabaseMetaData.class);
        registry = mock(DSLRegistry.class);
        expectedDsl = mock(DSL.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");
    }

    @Nested
    class LookupSucceeds {

        @Test
        void returnsDslForDetectedDialect() {
            when(registry.dslFor(MysqlDialectPlugin.DIALECT_NAME)).thenReturn(new Result.Success<>(expectedDsl));

            DSL result = DialectDetector.detect(dataSource, registry);

            assertThat(result).isSameAs(expectedDsl);
        }
    }

    @Nested
    class NoPluginAvailable {

        @Test
        void fallsBackToStandardSql() {
            when(registry.dslFor(MysqlDialectPlugin.DIALECT_NAME)).thenReturn(new Result.Failure<>("No plugin found"));
            when(registry.dslFor(StandardSQLDialectPlugin.DIALECT_NAME)).thenReturn(new Result.Success<>(expectedDsl));

            DSL result = DialectDetector.detect(dataSource, registry);

            assertThat(result).isSameAs(expectedDsl);
        }

        @Test
        void throwsWhenBothDialectAndFallbackFail() {
            when(registry.dslFor(MysqlDialectPlugin.DIALECT_NAME)).thenReturn(new Result.Failure<>("No plugin found"));
            when(registry.dslFor(StandardSQLDialectPlugin.DIALECT_NAME))
                    .thenReturn(new Result.Failure<>("No fallback"));

            assertThatThrownBy(() -> DialectDetector.detect(dataSource, registry))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot detect SQL dialect")
                    .hasMessageContaining(MysqlDialectPlugin.DIALECT_NAME);
        }
    }

    @Nested
    class ConnectionFailure {

        @Test
        void wrapsSqlExceptionInIllegalState() throws SQLException {
            when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

            assertThatThrownBy(() -> DialectDetector.detect(dataSource, registry))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to detect database dialect")
                    .hasCauseInstanceOf(SQLException.class);
        }
    }
}
