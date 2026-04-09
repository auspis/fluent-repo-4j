package io.github.auspis.fluentrepo4j.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.dialect.DialectDetector;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies that {@link FluentRepositoriesAutoConfiguration} is correctly ordered after
 * DataSource auto-configuration, so that {@link DSL} and {@link FluentConnectionProvider}
 * beans are created even when the {@link DataSource} itself comes from autoconfiguration
 * (e.g. via {@code spring.datasource.*} properties) rather than from user-defined beans.
 *
 * <p>Without explicit ordering after DataSource auto-configuration, the
 * {@code @ConditionalOnSingleCandidate(DataSource.class)} guards would evaluate before the
 * DataSource bean definition is registered, causing both beans to be silently skipped.
 */
class AutoConfigurationOrderingTest {

    private static final String BOOT3_DATASOURCE_AUTOCONFIGURATION =
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration";

    private static final String BOOT4_DATASOURCE_AUTOCONFIGURATION =
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    resolveDataSourceAutoConfigurationClass(), FluentRepositoriesAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:ordering-test;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=");

    private static Class<?> resolveDataSourceAutoConfigurationClass() {
        try {
            return Class.forName(BOOT3_DATASOURCE_AUTOCONFIGURATION);
        } catch (ClassNotFoundException ignored) {
            return resolveBoot4DataSourceAutoConfigurationClass();
        }
    }

    private static Class<?> resolveBoot4DataSourceAutoConfigurationClass() {
        try {
            return Class.forName(BOOT4_DATASOURCE_AUTOCONFIGURATION);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "Cannot resolve Spring Boot DataSourceAutoConfiguration class for Boot 3 or Boot 4.", exception);
        }
    }

    @Test
    void fluentBeansAreCreatedWhenDataSourceComesFromAutoConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DSL.class);
            assertThat(context).hasSingleBean(FluentConnectionProvider.class);
            assertThat(context).hasSingleBean(DSLRegistry.class);
        });
    }

    @Test
    void userDefinedDslBeanTakesPrecedenceOverAutoconfigured() {
        contextRunner.withUserConfiguration(CustomDslConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DSL.class);
            assertThat(context.getBean(DSL.class)).isSameAs(context.getBean("customDsl", DSL.class));
        });
    }

    @Test
    void userDefinedConnectionProviderBeanTakesPrecedenceOverAutoconfigured() {
        contextRunner
                .withUserConfiguration(CustomConnectionProviderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FluentConnectionProvider.class);
                    assertThat(context.getBean(FluentConnectionProvider.class))
                            .isSameAs(context.getBean("customConnectionProvider", FluentConnectionProvider.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDslConfiguration {

        @Bean("customDsl")
        DSL customDsl(DataSource dataSource) {
            return DialectDetector.detect(dataSource, DSLRegistry.createWithServiceLoader());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomConnectionProviderConfiguration {

        @Bean("customConnectionProvider")
        FluentConnectionProvider customConnectionProvider(DataSource dataSource) {
            return new FluentConnectionProvider(dataSource);
        }
    }
}
