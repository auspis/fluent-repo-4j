package io.github.auspis.fluentrepo4j.dialect;

import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.plugin.builtin.sql2016.StandardSQLDialectPlugin;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Detects the database dialect from a {@link DataSource} and returns
 * the matching fluent-sql-4j {@link DSL} instance.
 * <p>
 * Detection reads {@link DatabaseMetaData#getDatabaseProductName()} and
 * delegates to {@link DSLRegistry#dslFor(String)}, which returns the
 * first available plugin for that dialect regardless of version.
 * Falls back to {@code standardsql} when no dialect-specific plugin matches.
 * </p>
 * <p>
 * This class lives in fluent-repo-4j (not in fluent-sql-4j) because
 * {@link DSLRegistry} is a pure lookup registry with no JDBC dependency.
 * The detection responsibility — opening a connection and reading
 * {@link DatabaseMetaData} — belongs to the integration layer.
 * </p>
 */
public final class DialectDetector {

    private DialectDetector() {}

    /**
     * Auto-detects the database dialect from the given {@link DataSource}.
     * <p>
     * Tries the detected product name first; falls back to StandardSQL
     * when no dedicated plugin is available (e.g. H2, HSQLDB).
     * </p>
     *
     * @param dataSource the data source to inspect
     * @param registry   the DSL plugin registry
     * @return a DSL instance for the detected dialect
     * @throws IllegalStateException if no plugin matches and no fallback is available
     */
    public static DSL detect(DataSource dataSource, DSLRegistry registry) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();

            var result = registry.dslFor(productName);
            if (result.isSuccess()) {
                return result.orElseThrow();
            }
            var fallback = registry.dslFor(StandardSQLDialectPlugin.DIALECT_NAME);
            if (fallback.isSuccess()) {
                return fallback.orElseThrow();
            }
            throw new IllegalStateException("Cannot detect SQL dialect for database: " + productName
                    + ". Ensure a fluent-sql-4j dialect plugin is on the classpath.");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to detect database dialect from DataSource", e);
        }
    }
}
