package io.github.auspis.fluentrepo4j.repository;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.query.runtime.FluentQueryLookupStrategy;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.util.Optional;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.lang.Nullable;

/**
 * Factory that creates {@link FluentRepository} instances.
 * Extends Spring Data's {@link RepositoryFactorySupport} to integrate with the
 * standard repository proxy infrastructure.
 *
 * <p>Registers {@link FluentQueryLookupStrategy} so that method-name-derived
 * queries (e.g. {@code findByEmailAndName}) are resolved at repository
 * bootstrap time and executed via the fluent-sql-4j DSL.
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
        FluentEntityInformation entityInformation = new FluentEntityInformation<>(information.getDomainType());
        return new FluentRepository<>(entityInformation, connectionProvider, dsl);
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return FluentRepository.class;
    }

    @Override
    public <T, ID> EntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
        return new FluentEntityInformation<>(domainClass);
    }

    /**
     * Returns a {@link FluentQueryLookupStrategy} that resolves repository method
     * names to {@link io.github.auspis.fluentrepo4j.query.runtime.FluentRepositoryQuery}
     * instances via Spring Data's {@code PartTree}.
     */
    @Override
    protected Optional<QueryLookupStrategy> getQueryLookupStrategy(
            @Nullable Key key, ValueExpressionDelegate valueExpressionDelegate) {
        return Optional.of(new FluentQueryLookupStrategy(connectionProvider, dsl));
    }

    /**
     * Backward-compatibility override for Spring Data versions that still call the
     * deprecated two-argument form of {@code getQueryLookupStrategy}. Can be removed
     * once Spring Data 4.x (which dropped this overload) is the minimum required version.
     *
     * @deprecated Use {@link #getQueryLookupStrategy(Key, ValueExpressionDelegate)} instead.
     */
    @Override
    protected Optional<QueryLookupStrategy> getQueryLookupStrategy(
            @Nullable Key key,
            org.springframework.data.repository.query.QueryMethodEvaluationContextProvider evaluationContextProvider) {
        return Optional.of(new FluentQueryLookupStrategy(connectionProvider, dsl));
    }

    /** Exposes the underlying {@link ProjectionFactory} for testing. */
    @Override
    public ProjectionFactory getProjectionFactory() {
        return super.getProjectionFactory();
    }
}
