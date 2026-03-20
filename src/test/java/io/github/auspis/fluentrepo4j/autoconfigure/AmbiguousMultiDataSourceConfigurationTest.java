package io.github.auspis.fluentrepo4j.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.config.EnableFluentRepositories;
import io.github.auspis.fluentrepo4j.multids.PrimaryUserRepository;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

class AmbiguousMultiDataSourceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FluentRepositoriesAutoConfiguration.class))
            .withUserConfiguration(AmbiguousRepositoryConfiguration.class, AmbiguousInfrastructureConfiguration.class);

    @Test
    void contextFailsWhenMultipleDataSourcesExistWithoutExplicitRef() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessageContaining("Multiple beans of type DataSource were found for Fluent repositories")
                    .hasMessageContaining("Specify dataSourceRef on @EnableFluentRepositories");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFluentRepositories(
            basePackageClasses = PrimaryUserRepository.class,
            includeFilters =
                    @org.springframework.context.annotation.ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = PrimaryUserRepository.class))
    static class AmbiguousRepositoryConfiguration {}

    @Configuration(proxyBeanMethods = false)
    static class AmbiguousInfrastructureConfiguration {

        @Bean
        DataSource firstDataSource() {
            return createDataSource("jdbc:h2:mem:ambiguous-first-db;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        }

        @Bean
        DataSource secondDataSource() {
            return createDataSource("jdbc:h2:mem:ambiguous-second-db;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new DataSourceTransactionManager(firstDataSource());
        }

        @Bean
        DSLRegistry fluentDslRegistry() {
            return DSLRegistry.createWithServiceLoader();
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
}
