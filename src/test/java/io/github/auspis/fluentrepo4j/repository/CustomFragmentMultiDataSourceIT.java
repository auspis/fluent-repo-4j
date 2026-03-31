package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.config.EnableFluentRepositories;
import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.dialect.DialectDetector;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentrepo4j.test.fragment.PrimaryCustomQueriesImpl;
import io.github.auspis.fluentrepo4j.test.fragment.PrimaryUserWithCustomRepository;
import io.github.auspis.fluentrepo4j.test.fragment.SecondaryCustomQueriesImpl;
import io.github.auspis.fluentrepo4j.test.fragment.SecondaryUserWithCustomRepository;
import io.github.auspis.fluentrepo4j.test.util.DataSourceTestUtil;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest;

import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Integration test verifying that custom fragment implementations receive
 * the correct repository-specific context in a multi-datasource configuration.
 *
 * <p>Primary and secondary datasource each have their own repository group with
 * custom fragment queries. This test verifies:
 * <ul>
 *   <li>Each fragment receives a distinct context</li>
 *   <li>Custom queries execute against the correct datasource</li>
 *   <li>Data isolation between datasources is maintained</li>
 * </ul>
 */
@IntegrationTest
@SpringBootTest(
        classes = {
            CustomFragmentMultiDataSourceIT.TestApp.class,
            CustomFragmentMultiDataSourceIT.PrimaryConfig.class,
            CustomFragmentMultiDataSourceIT.SecondaryConfig.class
        })
@TestPropertySource(properties = "logging.level.io.github.auspis.fluentrepo4j=DEBUG")
class CustomFragmentMultiDataSourceIT {

    @Autowired
    private PrimaryUserWithCustomRepository primaryRepository;

    @Autowired
    private SecondaryUserWithCustomRepository secondaryRepository;

    @Autowired
    @Qualifier("primaryDataSource") private DataSource primaryDataSource;

    @Autowired
    @Qualifier("secondaryDataSource") private DataSource secondaryDataSource;

    @BeforeEach
    void setUp() throws SQLException {
        DataSourceTestUtil.createUsersTable(primaryDataSource);
        DataSourceTestUtil.createUsersTable(secondaryDataSource);

        DataSourceTestUtil.insertUser(primaryDataSource, 1L, "Primary Alice", "alice@primary.com");
        DataSourceTestUtil.insertUser(primaryDataSource, 2L, "Primary Bob", "bob@primary.com");

        DataSourceTestUtil.insertUser(secondaryDataSource, 10L, "Secondary Alice", "alice@secondary.com");
        DataSourceTestUtil.insertUser(secondaryDataSource, 20L, "Secondary Charlie", "charlie@secondary.com");
    }

    @Nested
    class DataIsolation {

        @Test
        void primaryCustomQueryReturnsOnlyPrimaryData() {
            List<User> result = primaryRepository.findUsersByNamePrefix("Primary");

            assertThat(result).hasSize(2).extracting(User::getName).allMatch(name -> name.startsWith("Primary"));
        }

        @Test
        void secondaryCustomQueryReturnsOnlySecondaryData() {
            List<User> result = secondaryRepository.findUsersByNamePrefix("Secondary");

            assertThat(result).hasSize(2).extracting(User::getName).allMatch(name -> name.startsWith("Secondary"));
        }

        @Test
        void primaryDoesNotSeeSecondaryData() {
            assertThat(primaryRepository.findUsersByNamePrefix("Secondary")).isEmpty();
        }

        @Test
        void secondaryDoesNotSeePrimaryData() {
            assertThat(secondaryRepository.findUsersByNamePrefix("Primary")).isEmpty();
        }
    }

    @Nested
    class ContextIsolation {

        @Autowired
        private PrimaryCustomQueriesImpl primaryImpl;

        @Autowired
        private SecondaryCustomQueriesImpl secondaryImpl;

        @Test
        void primaryAndSecondaryReceiveDistinctContexts() {
            assertThat(primaryImpl.getContext()).isNotNull();
            assertThat(secondaryImpl.getContext()).isNotNull();
            assertThat(primaryImpl.getContext()).isNotSameAs(secondaryImpl.getContext());
        }

        @Test
        void primaryAndSecondaryHaveDistinctConnectionProviders() {
            assertThat(primaryImpl.getContext().connectionProvider())
                    .isNotSameAs(secondaryImpl.getContext().connectionProvider());
        }
    }

    @Nested
    class CrudAndCustomCoexist {

        @Test
        void crudOnPrimaryWorks() {
            assertThat(primaryRepository.count()).isEqualTo(2L);
            assertThat(primaryRepository.findById(1L)).isPresent();
            assertThat(primaryRepository.findById(10L)).isNotPresent();
        }

        @Test
        void crudOnSecondaryWorks() {
            assertThat(secondaryRepository.count()).isEqualTo(2L);
            assertThat(secondaryRepository.findById(10L)).isPresent();
            assertThat(primaryRepository.findById(10L)).isNotPresent();
        }

        @Test
        void saveAndCustomQueryOnSameDatasource() {
            primaryRepository.save(new User("Primary Zara", "zara@primary.com").withId(3L));

            List<User> result = primaryRepository.findUsersByNamePrefix("Primary Z");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Primary Zara");
        }
    }

    // -- Configuration --

    @EnableAutoConfiguration
    static class TestApp {}

    @Configuration(proxyBeanMethods = false)
    @EnableFluentRepositories(
            basePackageClasses = PrimaryUserWithCustomRepository.class,
            includeFilters =
                    @ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = PrimaryUserWithCustomRepository.class),
            dataSourceRef = "primaryDataSource",
            dslRegistryRef = "sharedDslRegistry",
            transactionManagerRef = "primaryTransactionManager")
    static class PrimaryConfig {

        @Bean
        DataSource primaryDataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl(
                    "jdbc:h2:mem:fragment-primary;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean
        PlatformTransactionManager primaryTransactionManager(@Qualifier("primaryDataSource") DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        DSLRegistry sharedDslRegistry() {
            return DSLRegistry.createWithServiceLoader();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFluentRepositories(
            basePackageClasses = SecondaryUserWithCustomRepository.class,
            includeFilters =
                    @ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = SecondaryUserWithCustomRepository.class),
            connectionProviderRef = "secondaryConnectionProvider",
            dslRef = "secondaryDsl",
            transactionManagerRef = "secondaryTransactionManager")
    static class SecondaryConfig {

        @Bean
        DataSource secondaryDataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl(
                    "jdbc:h2:mem:fragment-secondary;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
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
}
