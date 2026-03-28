package io.github.auspis.fluentrepo4j.repository.context;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.util.Objects;

/**
 * Immutable context carrying the repository-specific {@link DSL} and
 * {@link FluentConnectionProvider} resolved at bootstrap time.
 *
 * <p>In multi-datasource configurations each repository group (defined via
 * {@link io.github.auspis.fluentrepo4j.config.EnableFluentRepositories @EnableFluentRepositories})
 * receives its own context, bound to the {@code DataSource} and dialect configured for that group.
 * Custom fragment implementations that implement {@link FluentRepositoryContextAware} receive
 * this context automatically, ensuring they execute queries against the correct datasource
 * with the correct SQL dialect.
 *
 * @param dsl                the dialect-specific DSL instance for this repository group
 * @param connectionProvider the connection provider bound to this repository group's datasource
 */
public record FluentRepositoryContext(DSL dsl, FluentConnectionProvider connectionProvider) {

    public FluentRepositoryContext {
        Objects.requireNonNull(dsl, "DSL must not be null");
        Objects.requireNonNull(connectionProvider, "FluentConnectionProvider must not be null");
    }
}
