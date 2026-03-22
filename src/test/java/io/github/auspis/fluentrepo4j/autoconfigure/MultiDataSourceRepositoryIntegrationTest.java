package io.github.auspis.fluentrepo4j.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.autoconfigure.util.AutoConfigureDataSourceTestUtil;
import io.github.auspis.fluentrepo4j.config.EnableFluentRepositories;
import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.dialect.DialectDetector;
import io.github.auspis.fluentrepo4j.test.autoconfigure.datasource.PrimaryUserRepository;
import io.github.auspis.fluentrepo4j.test.autoconfigure.datasource.SecondaryUserRepository;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

@IntegrationTest
@SpringBootTest(
        classes = {
            MultiDataSourceRepositoryIntegrationTest.MultiDataSourceTestApplication.class,
            MultiDataSourceRepositoryIntegrationTest.PrimaryRepositoryConfiguration.class,
            MultiDataSourceRepositoryIntegrationTest.SecondaryRepositoryConfiguration.class
        })
@TestPropertySource(properties = "logging.level.io.github.auspis.fluentrepo4j=DEBUG")
class MultiDataSourceRepositoryIntegrationTest {

    @Autowired
    private PrimaryUserRepository primaryUserRepository;

    @Autowired
    private SecondaryUserRepository secondaryUserRepository;

    @Autowired
    @Qualifier("primaryDataSource") private DataSource primaryDataSource;

    @Autowired
    @Qualifier("secondaryDataSource") private DataSource secondaryDataSource;

    @BeforeEach
    void setUp() {
        AutoConfigureDataSourceTestUtil.resetUsersTable(primaryDataSource, 1L, "Primary User", "primary@example.com");
        AutoConfigureDataSourceTestUtil.resetUsersTable(
                secondaryDataSource, 2L, "Secondary User", "secondary@example.com");
    }

    @Test
    void repositoriesUseDifferentDataSources() {
        assertThat(primaryUserRepository.count()).isEqualTo(1L);
        assertThat(secondaryUserRepository.count()).isEqualTo(1L);

        assertThat(primaryUserRepository.findById(1L))
                .isPresent()
                .get()
                .extracting(User::getName)
                .isEqualTo("Primary User");
        assertThat(secondaryUserRepository.findById(2L))
                .isPresent()
                .get()
                .extracting(User::getName)
                .isEqualTo("Secondary User");

        primaryUserRepository.save(new User("Only Primary", "only-primary@example.com").withId(10L));
        secondaryUserRepository.save(new User("Only Secondary", "only-secondary@example.com").withId(20L));

        assertThat(primaryUserRepository.count()).isEqualTo(2L);
        assertThat(secondaryUserRepository.count()).isEqualTo(2L);
        assertThat(primaryUserRepository.findById(20L)).isEmpty();
        assertThat(secondaryUserRepository.findById(10L)).isEmpty();
    }

    @EnableAutoConfiguration
    static class MultiDataSourceTestApplication {}

    @Configuration(proxyBeanMethods = false)
    @EnableFluentRepositories(
            basePackageClasses = {PrimaryUserRepository.class, SecondaryUserRepository.class},
            includeFilters =
                    @org.springframework.context.annotation.ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = PrimaryUserRepository.class),
            dataSourceRef = "primaryDataSource",
            dslRegistryRef = "customDslRegistry",
            transactionManagerRef = "primaryTransactionManager")
    static class PrimaryRepositoryConfiguration {

        @Bean
        DataSource primaryDataSource() {
            return createDataSource(
                    "jdbc:h2:mem:primary-repo-db;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        }

        @Bean
        PlatformTransactionManager primaryTransactionManager(@Qualifier("primaryDataSource") DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        DSLRegistry customDslRegistry() {
            return DSLRegistry.createWithServiceLoader();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFluentRepositories(
            basePackageClasses = {PrimaryUserRepository.class, SecondaryUserRepository.class},
            includeFilters =
                    @org.springframework.context.annotation.ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = SecondaryUserRepository.class),
            connectionProviderRef = "secondaryConnectionProvider",
            dslRef = "secondaryDsl",
            transactionManagerRef = "secondaryTransactionManager")
    static class SecondaryRepositoryConfiguration {

        @Bean
        DataSource secondaryDataSource() {
            return createDataSource(
                    "jdbc:h2:mem:secondary-repo-db;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        }

        @Bean
        PlatformTransactionManager secondaryTransactionManager(
                @Qualifier("secondaryDataSource") DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        FluentConnectionProvider secondaryConnectionProvider(@Qualifier("secondaryDataSource") DataSource dataSource) {
            return new FluentConnectionProvider(dataSource);
        }

        @Bean
        DSL secondaryDsl(@Qualifier("secondaryDataSource") DataSource dataSource) {
            DSLRegistry registry = DSLRegistry.createWithServiceLoader();
            return DialectDetector.detect(dataSource, registry);
        }
    }

    private static DataSource createDataSource(String url) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
