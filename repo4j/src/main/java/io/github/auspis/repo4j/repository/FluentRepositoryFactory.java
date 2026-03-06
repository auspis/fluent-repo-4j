package io.github.auspis.repo4j.repository;

import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.repo4j.connection.FluentConnectionProvider;
import io.github.auspis.repo4j.mapping.FluentEntityInformation;

/**
 * Factory that creates {@link SimpleFluentRepository} instances.
 * Extends Spring Data's {@link RepositoryFactorySupport} to integrate with the
 * standard repository proxy infrastructure.
 */
public class FluentRepositoryFactory extends RepositoryFactorySupport {

    private final FluentConnectionProvider connectionProvider;
    private final DSL dsl;

    public FluentRepositoryFactory(FluentConnectionProvider connectionProvider, DSL dsl) {
        this.connectionProvider = connectionProvider;
        this.dsl = dsl;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Object getTargetRepository(RepositoryInformation information) {
        FluentEntityInformation entityInformation =
                new FluentEntityInformation<>(information.getDomainType());
        return new SimpleFluentRepository<>(entityInformation, connectionProvider, dsl);
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return SimpleFluentRepository.class;
    }

    @Override
    public <T, ID> EntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
        return new FluentEntityInformation<>(domainClass);
    }
}
