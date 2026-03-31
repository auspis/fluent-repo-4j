package io.github.auspis.fluentrepo4j.repository.context;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.util.Objects;

/**
 * Immutable carrier for the infrastructure components (DSL and connection provider)
 * resolved at bootstrap time for a repository group.
 *
 * <p>Package-private — only {@link FluentRepositoryContext} exposes these components
 * to custom fragment implementations.
 *
 * @param dsl                the dialect-specific DSL instance for this repository group
 * @param connectionProvider the connection provider bound to this repository group's datasource
 */
record FluentRepositoryInfrastructure(DSL dsl, FluentConnectionProvider connectionProvider) {

    FluentRepositoryInfrastructure {
        Objects.requireNonNull(dsl, "DSL must not be null");
        Objects.requireNonNull(connectionProvider, "FluentConnectionProvider must not be null");
    }
}
