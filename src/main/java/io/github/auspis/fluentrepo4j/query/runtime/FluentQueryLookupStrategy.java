package io.github.auspis.fluentrepo4j.query.runtime;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.RepositoryQuery;

/**
 * {@link QueryLookupStrategy} that resolves repository method names to
 * {@link FluentRepositoryQuery} instances via
 * {@link io.github.auspis.fluentrepo4j.parse.PartTreeAdapter}.
 *
 * <p>Parsed {@link io.github.auspis.fluentrepo4j.query.QueryDescriptor}s are cached
 * per {@link Method} inside the {@link FluentRepositoryQuery} itself (at
 * construction time).  This strategy caches the resolved {@link RepositoryQuery}
 * objects per method, so parsing only happens once.
 */
public class FluentQueryLookupStrategy implements QueryLookupStrategy {

    private final FluentConnectionProvider connectionProvider;
    private final DSL dsl;
    private final ConcurrentMap<Method, RepositoryQuery> cache = new ConcurrentHashMap<>();

    public FluentQueryLookupStrategy(FluentConnectionProvider connectionProvider, DSL dsl) {
        this.connectionProvider = connectionProvider;
        this.dsl = dsl;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RepositoryQuery resolveQuery(
            Method method, RepositoryMetadata metadata, ProjectionFactory factory, NamedQueries namedQueries) {

        return cache.computeIfAbsent(method, m -> {
            FluentEntityInformation entityInformation = new FluentEntityInformation<>(metadata.getDomainType());
            return new FluentRepositoryQuery<>(m, metadata, factory, entityInformation, connectionProvider, dsl);
        });
    }
}
