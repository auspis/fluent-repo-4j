package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.config.EnableFluentRepositories;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentrepo4j.test.fragment.postfix.PostfixQueriesCustom;
import io.github.auspis.fluentrepo4j.test.fragment.postfix.PostfixUserRepository;
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
 * Integration test verifying that custom fragments with a non-default
 * {@code repositoryImplementationPostfix} are correctly discovered and
 * receive the Fluent context.
 */
@IntegrationTest
@SpringBootTest(classes = CustomPostfixIntegrationTest.TestConfig.class)
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:custom-postfix-test;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "logging.level.io.github.auspis.fluentrepo4j=DEBUG"
        })
class CustomPostfixIntegrationTest {

    @Autowired
    private PostfixUserRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        DataSourceTestUtil.createUsersTable(dataSource);
        DataSourceTestUtil.insertUser(dataSource, 1L, "John Doe", "john@example.com");
        DataSourceTestUtil.insertUser(dataSource, 2L, "Jane Doe", "jane@example.com");
    }

    @Nested
    class CustomPostfixDiscovery {

        @Test
        void findUsersByEmailReturnsMatch() {
            List<User> result = repository.findUsersByEmail("john@example.com");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("John Doe");
        }

        @Test
        void findUsersByEmailNoMatch() {
            List<User> result = repository.findUsersByEmail("nobody@example.com");

            assertThat(result).isEmpty();
        }

        @Test
        void crudMethodsStillWork() {
            assertThat(repository.count()).isEqualTo(2L);
            assertThat(repository.findById(1L)).isPresent();
        }
    }

    @Nested
    class ContextInjection {

        @Autowired
        private PostfixQueriesCustom impl;

        @Test
        void fragmentReceivedContext() {
            assertThat(impl.getContext()).isNotNull();
            assertThat(impl.getContext().dsl()).isNotNull();
            assertThat(impl.getContext().connectionProvider()).isNotNull();
        }
    }

    @EnableAutoConfiguration
    @EnableFluentRepositories(
            basePackageClasses = PostfixUserRepository.class,
            repositoryImplementationPostfix = "Custom",
            includeFilters =
                    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PostfixUserRepository.class))
    static class TestConfig {}
}
