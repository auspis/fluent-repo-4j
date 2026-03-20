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
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;

class PrimaryDataSourceFallbackConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FluentRepositoriesAutoConfiguration.class))
            .withUserConfiguration(PrimaryRepositoryConfiguration.class, PrimaryInfrastructureConfiguration.class);

    @Test
    void repositoriesWithoutExplicitRefsUsePrimaryDataSource() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            DataSource primaryDataSource = context.getBean("firstDataSource", DataSource.class);
            DataSource secondaryDataSource = context.getBean("secondDataSource", DataSource.class);
            PrimaryUserRepository repository = context.getBean(PrimaryUserRepository.class);

            resetDatabase(primaryDataSource, 1L, "Primary User", "primary@example.com");
            resetDatabase(secondaryDataSource, 2L, "Secondary User", "secondary@example.com");

            assertThat(repository.count()).isEqualTo(1L);
            assertThat(repository.findById(1L)).isPresent();
            assertThat(repository.findById(2L)).isEmpty();
        });
    }

    private static void resetDatabase(DataSource dataSource, long id, String name, String email) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        populator.execute(dataSource);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("INSERT INTO \"users\" (\"id\", \"name\", \"email\") VALUES (?, ?, ?)", id, name, email);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFluentRepositories(
            basePackageClasses = PrimaryUserRepository.class,
            includeFilters =
                    @org.springframework.context.annotation.ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = PrimaryUserRepository.class))
    static class PrimaryRepositoryConfiguration {}

    @Configuration(proxyBeanMethods = false)
    static class PrimaryInfrastructureConfiguration {

        @Bean
        @Primary
        DataSource firstDataSource() {
            return createDataSource("jdbc:h2:mem:primary-fallback-db;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        }

        @Bean
        DataSource secondDataSource() {
            return createDataSource("jdbc:h2:mem:secondary-fallback-db;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource firstDataSource) {
            return new DataSourceTransactionManager(firstDataSource);
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
