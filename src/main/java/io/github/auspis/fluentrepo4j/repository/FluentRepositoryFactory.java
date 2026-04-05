package io.github.auspis.fluentrepo4j.repository;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.functional.FunctionalCrudRepository;
import io.github.auspis.fluentrepo4j.functional.FunctionalPagingAndSortingRepository;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityWriter;
import io.github.auspis.fluentrepo4j.query.runtime.FluentQueryLookupStrategy;
import io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContext;
import io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContextAware;
import io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContextFactory;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.util.Optional;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.RepositoryFragment;
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

    /**
     * Intercepts repository creation to inject the repository-specific
     * {@link FluentRepositoryContext} into any custom fragment implementation that
     * implements {@link FluentRepositoryContextAware}.
     *
     * <p>User-provided custom fragments (the {@code ...Impl} classes auto-discovered by
     * Spring Data) are passed as the {@code fragments} parameter by the bean infrastructure.
     * This override iterates over them <em>before</em> proxying and injects the context
     * carrying the same {@code DSL} and {@code FluentConnectionProvider} that are used by
     * the base {@link FluentRepository} for this repository group, together with the
     * type-safe {@link FluentEntityRowMapper} and {@link FluentEntityWriter} resolved
     * from the repository's domain type.
     *
     * <p>In multi-datasource configurations this guarantees that each fragment receives the
     * correct datasource-specific DSL, rather than a globally shared instance. If a singleton
     * fragment bean is shared across repository groups with different infrastructure, an
     * {@link IllegalStateException} is thrown at bootstrap time.
     */
    @Override
    public <T> T getRepository(Class<T> repositoryInterface, RepositoryFragments fragments) {
        Class<?> domainType = getRepositoryMetadata(repositoryInterface).getDomainType();
        FluentRepositoryContext<?> context = FluentRepositoryContextFactory.create(dsl, connectionProvider, domainType);
        injectFluentContext(fragments, context);
        return super.getRepository(repositoryInterface, fragments);
    }

    @SuppressWarnings("unchecked")
    void injectFluentContext(RepositoryFragments fragments, FluentRepositoryContext<?> context) {
        for (RepositoryFragment<?> fragment : fragments) {
            fragment.getImplementation()
                    .filter(FluentRepositoryContextAware.class::isInstance)
                    .map(FluentRepositoryContextAware.class::cast)
                    .ifPresent(aware -> {
                        FluentRepositoryContext<?> existing = aware.getFluentRepositoryContext();
                        if (existing != null && !existing.hasSameInfrastructure(context)) {
                            throw new IllegalStateException(
                                    "Fragment " + aware.getClass().getName()
                                            + " is shared across repository groups with different datasources."
                                            + " Create a separate implementation per repository group.");
                        }
                        aware.setFluentRepositoryContext(context);
                    });
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Object getTargetRepository(RepositoryInformation information) {
        FluentEntityInformation entityInformation = new FluentEntityInformation<>(information.getDomainType());
        if (isFunctionalRepository(information.getRepositoryInterface())) {
            return new FunctionalFluentRepository<>(entityInformation, connectionProvider, dsl);
        }
        return new FluentRepository<>(entityInformation, connectionProvider, dsl);
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        if (isFunctionalRepository(metadata.getRepositoryInterface())) {
            return FunctionalFluentRepository.class;
        }
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

    /** Exposes the underlying {@link ProjectionFactory} for testing. */
    @Override
    public ProjectionFactory getProjectionFactory() {
        return super.getProjectionFactory();
    }

    static boolean isFunctionalRepository(Class<?> repositoryInterface) {
        return FunctionalCrudRepository.class.isAssignableFrom(repositoryInterface)
                || FunctionalPagingAndSortingRepository.class.isAssignableFrom(repositoryInterface);
    }
}
