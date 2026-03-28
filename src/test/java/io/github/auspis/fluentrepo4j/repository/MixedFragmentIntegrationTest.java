package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.config.EnableFluentRepositories;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentrepo4j.test.fragment.mixed.MixedAwareQueriesImpl;
import io.github.auspis.fluentrepo4j.test.fragment.mixed.MixedUserRepository;
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
 * Edge-case integration test: a single repository with both an
 * {@link io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContextAware}
 * fragment and a non-aware fragment. Proves they coexist without interference.
 */
@IntegrationTest
@SpringBootTest(classes = MixedFragmentIntegrationTest.TestConfig.class)
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:mixed-fragment-test;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "logging.level.io.github.auspis.fluentrepo4j=DEBUG"
        })
class MixedFragmentIntegrationTest {

    @Autowired
    private MixedUserRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        DataSourceTestUtil.createUsersTable(dataSource);
        DataSourceTestUtil.insertUser(dataSource, 1L, "John Doe", "john@example.com");
        DataSourceTestUtil.insertUser(dataSource, 2L, "Jane Doe", "jane@example.com");
    }

    @Nested
    class BothFragmentTypesCoexist {

        @Test
        void awareFragmentMethodWorks() {
            List<User> result = repository.findUsersByNameContaining("Doe");

            assertThat(result).hasSize(2);
        }

        @Test
        void nonAwareFragmentMethodWorks() {
            String greeting = repository.greetUser("John");

            assertThat(greeting).isEqualTo("Hello, John");
        }

        @Test
        void crudMethodsStillWork() {
            assertThat(repository.count()).isEqualTo(2L);
        }

        @Test
        void allThreeMethodTypesInSequence() {
            repository.save(new User("Alice Smith", "alice@example.com").withId(3L));
            assertThat(repository.count()).isEqualTo(3L);

            List<User> result = repository.findUsersByNameContaining("Alice");
            assertThat(result).hasSize(1);

            assertThat(repository.greetUser("Alice")).isEqualTo("Hello, Alice");
        }
    }

    @Nested
    class AwareFragmentReceivesContext {

        @Autowired
        private MixedAwareQueriesImpl customQueriesImpl;

        @Test
        void injectedContextIsNotNull() {
            assertThat(customQueriesImpl.getContext()).isNotNull();
            assertThat(customQueriesImpl.getContext().dsl()).isNotNull();
            assertThat(customQueriesImpl.getContext().connectionProvider()).isNotNull();
        }
    }

    @EnableAutoConfiguration
    @EnableFluentRepositories(
            basePackageClasses = MixedUserRepository.class,
            includeFilters =
                    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = MixedUserRepository.class))
    static class TestConfig {}
}
