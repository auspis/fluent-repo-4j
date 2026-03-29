package io.github.auspis.fluentrepo4j.repository.context;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityWriter;
import io.github.auspis.fluentsql4j.dsl.DSL;

/**
 * Factory for creating {@link FluentRepositoryContext} instances from raw infrastructure
 * components and a domain type. Used by the repository factory at bootstrap time.
 */
public final class FluentRepositoryContextFactory {

    private FluentRepositoryContextFactory() {}

    /**
     * Creates a fully-populated {@link FluentRepositoryContext} for the given domain type.
     *
     * @param dsl                the DSL instance for this repository group
     * @param connectionProvider the connection provider for this repository group
     * @param domainType         the entity class managed by the repository
     * @param <T>                the entity type
     * @return a new context carrying infrastructure, row mapper, and writer
     */
    @SuppressWarnings("unchecked")
    public static <T> FluentRepositoryContext<T> create(
            DSL dsl, FluentConnectionProvider connectionProvider, Class<?> domainType) {
        FluentRepositoryInfrastructure infrastructure = new FluentRepositoryInfrastructure(dsl, connectionProvider);
        FluentEntityInformation<T, ?> entityInfo = new FluentEntityInformation<>((Class<T>) domainType);
        FluentEntityRowMapper<T> rowMapper = new FluentEntityRowMapper<>(entityInfo);
        FluentEntityWriter<T> writer = new FluentEntityWriter<>(entityInfo);
        return new FluentRepositoryContext<>(infrastructure, rowMapper, writer);
    }
}
