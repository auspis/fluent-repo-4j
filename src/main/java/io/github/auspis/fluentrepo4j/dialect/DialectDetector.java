package io.github.auspis.fluentrepo4j.dialect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;

/**
 * Detects the database dialect from a {@link DataSource} and returns
 * the matching fluent-sql-4j {@link DSL} instance.
 * <p>
 * Falls back to {@code standardsql/2008} if no specific plugin matches.
 * </p>
 */

// TODO: is really needed? may we rely on fluent-sql-4j's own auto-detection in the DSLRegistry? maybe we can just delegate to it and let it handle the fallback logic?
// TODO: move to fluent-sql-4j in a separate module, e.g. fluent-sql-4j-autodetect, so it can be reused by other projects without depending on fluent-repo4j?
public final class DialectDetector {

    private DialectDetector() {
    }

    /**
     * Auto-detects the database dialect from the given {@link DataSource}.
     *
     * @param dataSource the data source to inspect
     * @param registry   the DSL plugin registry
     * @return a DSL instance for the detected dialect
     * @throws IllegalStateException if detection fails and no fallback is available
     */
    public static DSL detect(DataSource dataSource, DSLRegistry registry) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName().toLowerCase();
            String productVersion = meta.getDatabaseProductVersion();

            var result = registry.dslFor(productName, productVersion);
            if (result.isSuccess()) {
                return result.orElseThrow();
            }
            var fallback = registry.dslFor("standardsql", "2008");
            if (fallback.isSuccess()) {
                return fallback.orElseThrow();
            }
            throw new IllegalStateException(
                    "Cannot detect SQL dialect for database: " + productName
                            + " " + productVersion
                            + ". Ensure a fluent-sql-4j dialect plugin is on the classpath.");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to detect database dialect from DataSource", e);
        }
    }
}
