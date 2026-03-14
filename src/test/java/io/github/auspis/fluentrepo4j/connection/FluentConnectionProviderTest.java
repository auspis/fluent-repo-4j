package io.github.auspis.fluentrepo4j.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests for {@link FluentConnectionProvider}.
 * Verifies delegation to {@link DataSourceUtils} with and without active transactions.
 */
class FluentConnectionProviderTest {

    private DataSource dataSource;
    private FluentConnectionProvider provider;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:fluent_connection_provider_test;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");

        this.dataSource = ds;
        this.provider = new FluentConnectionProvider(dataSource);
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Nested
    class BasicBehavior {

        @Test
        void getDataSource() {
            assertThat(provider.getDataSource()).isSameAs(dataSource);
        }

        @Test
        void getConnection() throws SQLException {
            Connection connection = provider.getConnection();
            try {
                assertThat(connection).isNotNull();
                assertThat(connection.isClosed()).isFalse();
                assertThat(connection.getAutoCommit()).isTrue();
            } finally {
                provider.releaseConnection(connection);
            }
        }

        @Test
        void releaseConnection() throws SQLException {
            Connection connection = provider.getConnection();

            provider.releaseConnection(connection);

            assertThat(connection.isClosed()).isTrue();
        }

        @Test
        void releaseConnection_null() {
            assertThatCode(() -> provider.releaseConnection(null)).doesNotThrowAnyException();
        }
    }

    @Nested
    class TransactionalBehavior {

        @Test
        void getConnection() {
            transactionTemplate.executeWithoutResult(status -> {
                Connection providerConnection = provider.getConnection();
                Connection springConnection = DataSourceUtils.getConnection(dataSource);
                Connection secondProviderCall = provider.getConnection();

                assertThat(providerConnection).isSameAs(springConnection);
                assertThat(secondProviderCall).isSameAs(providerConnection);

                provider.releaseConnection(providerConnection);
            });
        }

        @Test
        void releaseConnection() {
            AtomicReference<Connection> txConnectionRef = new AtomicReference<>();

            transactionTemplate.executeWithoutResult(status -> {
                Connection connection = provider.getConnection();
                txConnectionRef.set(connection);

                provider.releaseConnection(connection);

                assertThatCode(() -> assertThat(connection.isClosed()).isFalse())
                        .doesNotThrowAnyException();
            });

            Connection connectionAfterCommit = txConnectionRef.get();
            assertThatCode(() -> assertThat(connectionAfterCommit.isClosed()).isTrue())
                    .doesNotThrowAnyException();
        }
    }
}
