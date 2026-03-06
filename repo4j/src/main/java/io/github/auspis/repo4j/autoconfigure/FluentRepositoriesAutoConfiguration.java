package io.github.auspis.repo4j.autoconfigure;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.repo4j.config.EnableFluentRepositories;
import io.github.auspis.repo4j.connection.FluentConnectionProvider;
import io.github.auspis.repo4j.dialect.DialectDetector;

/**
 * Spring Boot {@link AutoConfiguration} for Fluent SQL repositories.
 * <p>
 * Automatically configures:
 * <ul>
 *   <li>{@link FluentConnectionProvider} from the available {@link DataSource}</li>
 *   <li>{@link DSL} via dialect auto-detection from the {@link DataSource}</li>
 * </ul>
 * <p>
 * Activates when both a {@link DataSource} bean and the fluent-sql-4j {@link DSL} class
 * are available on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass({DataSource.class, DSL.class})
@ConditionalOnBean(DataSource.class)
@EnableFluentRepositories
public class FluentRepositoriesAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FluentConnectionProvider fluentConnectionProvider(DataSource dataSource) {
        return new FluentConnectionProvider(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public DSLRegistry fluentDslRegistry() {
        return DSLRegistry.createWithServiceLoader();
    }

    @Bean
    @ConditionalOnMissingBean
    public DSL fluentDsl(DataSource dataSource, DSLRegistry registry) {
        return DialectDetector.detect(dataSource, registry);
    }
}
