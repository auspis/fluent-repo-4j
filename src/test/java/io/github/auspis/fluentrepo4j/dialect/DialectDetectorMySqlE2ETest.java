package io.github.auspis.fluentrepo4j.dialect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.repository.FluentRepository;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.plugin.builtin.mysql.dsl.MysqlDSL;
import io.github.auspis.fluentsql4j.test.util.annotation.E2ETest;
import io.github.auspis.fluentsql4j.test.util.database.TestDatabaseUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * E2E smoke test verifying {@link DialectDetector} and basic CRUD
 * against a real MySQL database via Testcontainers.
 */
@E2ETest
@Testcontainers
class DialectDetectorMySqlE2ETest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    private SingleConnectionDataSource dataSource;
    private FluentRepository<User, Long> repository;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new SingleConnectionDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword(), true);

        try (Connection connection = dataSource.getConnection()) {
            TestDatabaseUtil.MySQL.dropUsersTable(connection);
            TestDatabaseUtil.MySQL.createUsersTable(connection);
        }

        DSLRegistry registry = DSLRegistry.createWithServiceLoader();
        DSL dsl = DialectDetector.detect(dataSource, registry);
        FluentConnectionProvider connectionProvider = new FluentConnectionProvider(dataSource);
        FluentEntityInformation<User, Long> entityInfo = new FluentEntityInformation<>(User.class);

        repository = new FluentRepository<>(entityInfo, connectionProvider, dsl);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void detectReturnsDsl() {
        DSLRegistry registry = DSLRegistry.createWithServiceLoader();

        DSL dsl = DialectDetector.detect(dataSource, registry);

        assertThat(dsl).isNotNull().isInstanceOf(MysqlDSL.class);
    }

    @Test
    void saveAndFindById() {
        User user = new User("Alice", "alice@example.com");
        user.setId(1L);

        repository.save(user);

        Optional<User> found = repository.findById(1L);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Alice");
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void saveUpdate() {
        User user = new User("Bob", "bob@example.com");
        user.setId(2L);
        repository.save(user);

        user.setName("Bob Updated");
        repository.save(user);

        Optional<User> found = repository.findById(2L);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Bob Updated");
    }
}
