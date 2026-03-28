package io.github.auspis.fluentrepo4j.query.runtime;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper;
import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentrepo4j.parse.PartTreeAdapter;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentrepo4j.query.QueryOperation;
import io.github.auspis.fluentrepo4j.query.mapper.dsl.QueryDescriptorToDslMapper;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;

/**
 * {@link RepositoryQuery} implementation for dynamic query methods derived from
 * Spring Data method names (e.g. {@code findByEmailAndName}).
 *
 * <p>Instances are created once per repository method by
 * {@link FluentQueryLookupStrategy} and hold a cached {@link QueryDescriptor}.
 * At execution time ({@link #execute(Object[])}) the mapper produces an
 * {@link ExecutableQuery} which carries the correct SQL operation;
 * this class then executes it and adapts the raw result to the method's
 * declared return type.
 *
 * @param <T>  entity type
 * @param <ID> identifier type
 */
public class FluentRepositoryQuery<T, ID> implements RepositoryQuery {

    private final QueryMethod queryMethod;
    private final QueryDescriptor descriptor;
    private final QueryDescriptorToDslMapper<T, ID> dslMapper;
    private final QueryExecutionResources<T> executionResources;

    public FluentRepositoryQuery(
            Method method,
            RepositoryMetadata metadata,
            ProjectionFactory projectionFactory,
            FluentEntityInformation<T, ID> entityInformation,
            FluentConnectionProvider connectionProvider,
            DSL dsl) {
        // TODO: [URGENT] switch to non deprecated QueryMethod constructor once we require Spring Data 3.0+
        this.queryMethod = new QueryMethod(method, metadata, projectionFactory);
        this.executionResources = new QueryExecutionResources<>(
                connectionProvider,
                new FluentEntityRowMapper<>(entityInformation),
                new SQLExceptionSubclassTranslator());

        PropertyMetadataProvider<T, ID> metaProvider = new PropertyMetadataProvider<>(entityInformation);
        this.dslMapper = new QueryDescriptorToDslMapper<>(dsl, metaProvider);

        // Build and cache the descriptor at construction time
        this.descriptor = PartTreeAdapter.adapt(method, metadata.getDomainType());
    }

    @Override
    public Object execute(Object[] parameters) {
        Object[] args = parameters != null ? parameters : new Object[0];
        ExecutableQuery<T> executable = dslMapper.map(descriptor, args);
        Object rawResult = executable.execute(executionResources);
        return adaptReturnType(rawResult, args);
    }

    @Override
    public QueryMethod getQueryMethod() {
        return queryMethod;
    }

    // ---- Return-type adaptation ----

    @SuppressWarnings("unchecked")
    private Object adaptReturnType(Object rawResult, Object[] args) {
        if (rawResult instanceof Long || rawResult instanceof Boolean) {
            return rawResult;
        }
        if (rawResult instanceof Integer affected) {
            return adaptDeleteResult(affected);
        }
        List<T> results = (List<T>) rawResult;
        return adaptSelectResult(results, args);
    }

    private Object adaptDeleteResult(int affected) {
        Class<?> returnType = queryMethod.getReturnedObjectType();
        if (long.class.equals(returnType) || Long.class.equals(returnType)) {
            return (long) affected;
        }
        if (int.class.equals(returnType) || Integer.class.equals(returnType)) {
            return affected;
        }
        return null; // void
    }

    private Object adaptSelectResult(List<T> results, Object[] args) {
        Class<?> returnType = queryMethod.getReturnedObjectType();

        if (queryMethod.isPageQuery()) {
            return adaptAsPage(results, args);
        }
        if (queryMethod.isSliceQuery()) {
            return adaptAsSlice(results, args);
        }
        if (queryMethod.isCollectionQuery() || queryMethod.isStreamQuery()) {
            if (Stream.class.isAssignableFrom(returnType)) {
                return results.stream();
            }
            return results;
        }
        if (Optional.class.isAssignableFrom(returnType)) {
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        }
        return results.isEmpty() ? null : results.get(0);
    }

    private Page<T> adaptAsPage(List<T> content, Object[] args) {
        Pageable pageable = extractPageable(args);
        Pageable effectivePageable = pageable != null ? pageable : Pageable.unpaged();

        QueryDescriptor countDescriptor = new QueryDescriptor(
                QueryOperation.COUNT,
                descriptor.distinct(),
                null,
                descriptor.predicateDescriptor(),
                List.of(),
                descriptor.pageableParamIndex(),
                descriptor.sortParamIndex());

        ExecutableQuery<T> countQuery = dslMapper.map(countDescriptor, args);
        long total = (long) countQuery.execute(executionResources);

        return new PageImpl<>(content, effectivePageable, total);
    }

    private Slice<T> adaptAsSlice(List<T> content, Object[] args) {
        Pageable pageable = extractPageable(args);
        Pageable effectivePageable = pageable != null ? pageable : Pageable.unpaged();
        return new SliceImpl<>(content, effectivePageable, !content.isEmpty());
    }

    private Pageable extractPageable(Object[] parameters) {
        if (descriptor.pageableParamIndex() >= 0
                && parameters != null
                && parameters.length > descriptor.pageableParamIndex()) {
            Object arg = parameters[descriptor.pageableParamIndex()];
            if (arg instanceof Pageable p) {
                return p;
            }
        }
        return null;
    }
}
