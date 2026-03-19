package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.dialect.DialectDetector;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.test.util.annotation.E2ETest;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@E2ETest
@Testcontainers
class PaginationPostgresE2ETest {

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
            stmt.execute(
                    "INSERT INTO \"users\" (\"id\", \"name\", \"email\", \"age\", \"active\") VALUES (1, 'Charlie', 'charlie@example.com', 30, true)");
            stmt.execute(
                    "INSERT INTO \"users\" (\"id\", \"name\", \"email\", \"age\", \"active\") VALUES (2, 'Alice', 'alice@example.com', 25, true)");
            stmt.execute(
                    "INSERT INTO \"users\" (\"id\", \"name\", \"email\", \"age\", \"active\") VALUES (3, 'Bob', 'bob@example.com', 35, true)");
            stmt.execute(
                    "INSERT INTO \"users\" (\"id\", \"name\", \"email\", \"age\", \"active\") VALUES (4, 'Diana', 'diana@example.com', 28, true)");
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
        }
    }

    @Test
    void findAllSorted_ascByName() {
        Iterable<User> users = repository.findAll(Sort.by("name"));

        assertThat(users).extracting(User::getName).containsExactly("Alice", "Bob", "Charlie", "Diana");
    }

    @Test
    void findAllSorted_descByAge() {
        Iterable<User> users = repository.findAll(Sort.by(Sort.Direction.DESC, "age"));

        assertThat(users).extracting(User::getAge).containsExactly(35, 30, 28, 25);
    }

    @Test
    void findAllPaged_firstPage() {
        Page<User> page = repository.findAll(PageRequest.of(0, 2, Sort.by("name")));

        assertThat(page.getContent()).extracting(User::getName).containsExactly("Alice", "Bob");
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findAllPaged_secondPage() {
        Page<User> page = repository.findAll(PageRequest.of(1, 2, Sort.by("name")));

        assertThat(page.getContent()).extracting(User::getName).containsExactly("Charlie", "Diana");
        assertThat(page.isLast()).isTrue();
    }

    @Test
    void findAllPaged_beyondLastPage() {
        Page<User> page = repository.findAll(PageRequest.of(5, 2));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(4);
    }
}
