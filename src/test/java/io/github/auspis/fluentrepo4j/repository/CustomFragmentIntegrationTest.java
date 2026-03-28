package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.config.EnableFluentRepositories;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentrepo4j.test.fragment.UserCustomQueriesImpl;
import io.github.auspis.fluentrepo4j.test.fragment.UserWithCustomQueriesRepository;
import io.github.auspis.fluentrepo4j.test.util.DataSourceTestUtil;
import io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest;

import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test verifying that custom repository fragments receive the
 * repository-specific {@link io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContext}
 * and execute queries against the correct datasource using the fluent DSL.
 */
@IntegrationTest
@SpringBootTest(classes = CustomFragmentIntegrationTest.TestConfig.class)
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:custom-fragment-test;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "logging.level.io.github.auspis.fluentrepo4j=DEBUG"
        })
class CustomFragmentIntegrationTest {

    @Autowired
    private UserWithCustomQueriesRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        DataSourceTestUtil.createUsersTable(dataSource);
        DataSourceTestUtil.insertUser(dataSource, 1L, "John Doe", "john@example.com");
        DataSourceTestUtil.insertUser(dataSource, 2L, "Jane Doe", "jane@example.com");
        DataSourceTestUtil.insertUser(dataSource, 3L, "Alice Smith", "alice@example.com");
    }

    @Nested
    class CustomFragmentMethods {

        @Test
        void findUsersByNameContaining() {
            List<User> result = repository.findUsersByNameContaining("Doe");

            assertThat(result).hasSize(2);
            assertThat(result).extracting(User::getName).containsExactlyInAnyOrder("John Doe", "Jane Doe");
        }

        @Test
        void findUsersByNameContainingNoMatch() {
            List<User> result = repository.findUsersByNameContaining("Nobody");

            assertThat(result).isEmpty();
        }

        @Test
        void countActiveUsers() {
            long count = repository.countActiveUsers();

            assertThat(count).isEqualTo(3L);
        }
    }

    @Nested
    class CrudAndCustomCoexist {

        @Test
        void crudMethodsStillWork() {
            assertThat(repository.count()).isEqualTo(3L);
            assertThat(repository.findById(1L)).isPresent();
        }

        @Test
        void customAndCrudInSameTransaction() {
            repository.save(new User("Bob Builder", "bob@example.com").withId(4L));

            assertThat(repository.count()).isEqualTo(4L);

            List<User> bobs = repository.findUsersByNameContaining("Bob");
            assertThat(bobs).hasSize(1);
            assertThat(bobs.get(0).getName()).isEqualTo("Bob Builder");
        }
    }

    @Nested
    class ContextInjection {

        @Autowired
        private UserCustomQueriesImpl customQueriesImpl;

        @Test
        void fragmentReceivedNonNullContext() {
            assertThat(customQueriesImpl.getContext()).isNotNull();
        }

        @Test
        void fragmentContextHasDsl() {
            assertThat(customQueriesImpl.getContext().dsl()).isNotNull();
        }

        @Test
        void fragmentContextHasConnectionProvider() {
            assertThat(customQueriesImpl.getContext().connectionProvider()).isNotNull();
        }
    }

    @EnableAutoConfiguration
    @EnableFluentRepositories(
            basePackageClasses = UserWithCustomQueriesRepository.class,
            includeFilters =
                    @ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = UserWithCustomQueriesRepository.class))
    static class TestConfig {}
}
