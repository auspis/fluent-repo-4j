package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.config.EnableFluentRepositories;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentrepo4j.test.fragment.nonaware.PlainUserRepository;
import io.github.auspis.fluentrepo4j.test.util.DataSourceTestUtil;
import io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest;

import java.sql.SQLException;
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
 * Integration test verifying that custom fragments NOT implementing
 * {@link io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContextAware}
 * continue to work correctly — the context injection is opt-in.
 */
@IntegrationTest
@SpringBootTest(classes = NonAwareFragmentIntegrationTest.TestConfig.class)
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:non-aware-test;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "logging.level.io.github.auspis.fluentrepo4j=DEBUG"
        })
class NonAwareFragmentIntegrationTest {

    @Autowired
    private PlainUserRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        DataSourceTestUtil.createUsersTable(dataSource);
        DataSourceTestUtil.insertUser(dataSource, 1L, "Alice Smith", "alice@example.com");
    }

    @Nested
    class NonAwareFragmentWorks {

        @Test
        void customMethodReturnsExpectedValue() {
            String greeting = repository.greetUser("Alice");

            assertThat(greeting).isEqualTo("Hello, Alice");
        }

        @Test
        void crudMethodsStillWork() {
            assertThat(repository.count()).isEqualTo(1L);
            assertThat(repository.findById(1L)).isPresent();
        }

        @Test
        void saveAndCustomMethodCoexist() {
            repository.save(new User("Bob Builder", "bob@example.com").withId(2L));

            assertThat(repository.count()).isEqualTo(2L);
            assertThat(repository.greetUser("Bob")).isEqualTo("Hello, Bob");
        }
    }

    @EnableAutoConfiguration
    @EnableFluentRepositories(
            basePackageClasses = PlainUserRepository.class,
            includeFilters =
                    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PlainUserRepository.class))
    static class TestConfig {}
}
