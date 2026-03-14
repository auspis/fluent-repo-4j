package io.github.auspis.fluentrepo4j.connection;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * Provides JDBC connections integrated with Spring's transaction management.
 * <p>
 * Uses {@link DataSourceUtils} to obtain connections that are bound to the current
 * Spring-managed transaction (if any). If no transaction is active, a new connection
 * is obtained in auto-commit mode.
 * </p>
 */
public class FluentConnectionProvider {

    private final DataSource dataSource;

    public FluentConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Obtains a connection from the configured {@link DataSource}.
     * If a Spring-managed transaction is active, returns the connection bound to that transaction.
     * Otherwise, returns a new auto-commit connection.
     */
    public Connection getConnection() {
        return DataSourceUtils.getConnection(dataSource);
    }

    /**
     * Releases the connection back to the pool.
     * If the connection is bound to a Spring-managed transaction, it is NOT closed
     * (the transaction manager will close it when the transaction completes).
     */
    public void releaseConnection(Connection connection) {
        DataSourceUtils.releaseConnection(connection, dataSource);
    }

    public DataSource getDataSource() {
        return dataSource;
    }
}
