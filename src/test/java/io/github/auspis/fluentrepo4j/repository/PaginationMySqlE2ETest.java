package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.dialect.DialectDetector;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.test.util.annotation.E2ETest;
import io.github.auspis.fluentsql4j.test.util.database.TestDatabaseUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@E2ETest
@Testcontainers
class PaginationMySqlE2ETest {

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
            TestDatabaseUtil.MySQL.insertSampleUsers(connection);
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
    void findAllSorted_ascByName() {
        Iterable<User> users = repository.findAll(Sort.by("name"));

        assertThat(users)
                .extracting(User::getName)
                .containsExactly(
                        "Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry", "Jane Smith", "John Doe");
    }

    @Test
    void findAllSorted_descByAge() {
        Iterable<User> users = repository.findAll(Sort.by(Sort.Direction.DESC, "age"));

        assertThat(users).extracting(User::getAge).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(users).hasSize(10);
    }

    @Test
    void findAllPaged_firstPage() {
        Page<User> page = repository.findAll(PageRequest.of(0, 2, Sort.by("name")));

        assertThat(page.getContent()).extracting(User::getName).containsExactly("Alice", "Bob");
        assertThat(page.getTotalElements()).isEqualTo(10);
        assertThat(page.getTotalPages()).isEqualTo(5);
    }

    @Test
    void findAllPaged_lastPage() {
        Page<User> page = repository.findAll(PageRequest.of(4, 2, Sort.by("name")));

        assertThat(page.getContent()).extracting(User::getName).containsExactly("Jane Smith", "John Doe");
        assertThat(page.isLast()).isTrue();
    }

    @Test
    void findAllPaged_beyondLastPage() {
        Page<User> page = repository.findAll(PageRequest.of(5, 2));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(10);
    }
}
