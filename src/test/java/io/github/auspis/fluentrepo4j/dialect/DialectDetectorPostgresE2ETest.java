package io.github.auspis.fluentrepo4j.dialect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.repository.SimpleFluentRepository;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.plugin.builtin.postgre.dsl.PostgreSqlDSL;
import io.github.auspis.fluentsql4j.test.util.annotation.E2ETest;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * E2E smoke test verifying {@link DialectDetector} and basic CRUD
 * against a real PostgreSQL database via Testcontainers.
 */
@E2ETest
@Testcontainers
class DialectDetectorPostgresE2ETest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    private SingleConnectionDataSource dataSource;
    private SimpleFluentRepository<User, Long> repository;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new SingleConnectionDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), true);

        try (Connection connection = dataSource.getConnection();
                Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"users\"");
            stmt.execute("""
                    CREATE TABLE "users" (
                        "id" BIGINT PRIMARY KEY,
                        "name" VARCHAR(50),
                        "email" VARCHAR(100),
                        "age" INT,
                        "active" BOOLEAN,
                        "birthdate" DATE,
                        "createdAt" TIMESTAMP,
                        "address" TEXT,
                        "preferences" TEXT
                    )
                    """);
        }

        DSLRegistry registry = DSLRegistry.createWithServiceLoader();
        DSL dsl = DialectDetector.detect(dataSource, registry);
        FluentConnectionProvider connectionProvider = new FluentConnectionProvider(dataSource);
        FluentEntityInformation<User, Long> entityInfo = new FluentEntityInformation<>(User.class);

        repository = new SimpleFluentRepository<>(entityInfo, connectionProvider, dsl);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
            dataSource = null;
        }
    }

    @Test
    void detectReturnsDsl() {
        DSLRegistry registry = DSLRegistry.createWithServiceLoader();

        DSL dsl = DialectDetector.detect(dataSource, registry);

        assertThat(dsl).isNotNull().isInstanceOf(PostgreSqlDSL.class);
    }

    @Test
    void saveAndFindById() {
        User user = new User("Alice", "alice@example.com", 30);
        user.setId(1L);

        repository.save(user);

        Optional<User> found = repository.findById(1L);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Alice");
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void saveUpdate() {
        User user = new User("Bob", "bob@example.com", 25);
        user.setId(2L);
        repository.save(user);

        user.setName("Bob Updated");
        repository.save(user);

        Optional<User> found = repository.findById(2L);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Bob Updated");
    }
}
