package io.github.auspis.fluentrepo4j.query.runtime;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Error;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Found;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.NotFound;
import io.github.auspis.fluentrepo4j.functional.write.WriteResult;
import io.github.auspis.fluentrepo4j.functional.write.WriteResult.Success;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper;
import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentrepo4j.parse.PartTreeAdapter;
import io.github.auspis.fluentrepo4j.query.OrderByClause;
import io.github.auspis.fluentrepo4j.query.PageWindow;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentrepo4j.query.QueryOperation;
import io.github.auspis.fluentrepo4j.query.QueryRuntimeParams;
import io.github.auspis.fluentrepo4j.query.mapper.dsl.QueryDescriptorToDslMapper;
import io.github.auspis.fluentsql4j.ast.dql.clause.Sorting;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
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
    private final PropertyMetadataProvider<T, ID> metadataProvider;
    private final boolean readFunctionalMode;
    private final boolean writeFunctionalMode;
    private final Class<?> functionalInnerType;

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
        this.metadataProvider = metaProvider;
        this.dslMapper = new QueryDescriptorToDslMapper<>(dsl, metaProvider);

        // Build and cache the descriptor at construction time
        this.descriptor = PartTreeAdapter.adapt(method, metadata.getDomainType());

        // Detect functional mode from return type
        this.readFunctionalMode = ReadResult.class.isAssignableFrom(method.getReturnType());
        this.writeFunctionalMode = WriteResult.class.isAssignableFrom(method.getReturnType());
        this.functionalInnerType =
                (readFunctionalMode || writeFunctionalMode) ? resolveFunctionalInnerType(method) : null;

        if (readFunctionalMode || writeFunctionalMode) {
            validateFunctionalReturnType(method);
        }
    }

    private void validateFunctionalReturnType(Method method) {
        QueryOperation operation = descriptor.operation();
        if (readFunctionalMode) {
            switch (operation) {
                case FIND -> validateReadFindInnerType(method);
                case COUNT -> validateReadCountInnerType(method);
                case EXISTS -> validateReadExistsInnerType(method);
                case DELETE ->
                    throw new IllegalStateException(
                            "ReadResult does not support delete-derived query methods: " + method.toGenericString());
            }
        }
        if (writeFunctionalMode) {
            switch (operation) {
                case DELETE -> validateWriteDeleteInnerType(method);
                case FIND, COUNT, EXISTS ->
                    throw new IllegalStateException(
                            "WriteResult is supported only for delete-derived query methods. Found: "
                                    + method.toGenericString());
            }
        }
    }

    private void validateReadFindInnerType(Method method) {
        if (List.class.isAssignableFrom(functionalInnerType)
                || Collection.class.isAssignableFrom(functionalInnerType)
                || Iterable.class.isAssignableFrom(functionalInnerType)
                || Stream.class.isAssignableFrom(functionalInnerType)
                || Page.class.isAssignableFrom(functionalInnerType)
                || Slice.class.isAssignableFrom(functionalInnerType)) {
            return;
        }
        if (Optional.class.isAssignableFrom(functionalInnerType)) {
            throw new IllegalStateException("ReadResult does not support Optional inner types. "
                    + "Use ReadResult<T> for single-result queries: "
                    + method.toGenericString());
        }
    }

    private void validateWriteDeleteInnerType(Method method) {
        if (Long.class.isAssignableFrom(functionalInnerType)
                || Integer.class.isAssignableFrom(functionalInnerType)
                || Boolean.class.isAssignableFrom(functionalInnerType)) {
            return;
        }
        throw new IllegalStateException("Unsupported WriteResult inner type for delete-derived query: "
                + functionalInnerType.getName()
                + " in method "
                + method.toGenericString()
                + ". Supported inner types are java.lang.Long, java.lang.Integer and java.lang.Boolean.");
    }

    private void validateReadCountInnerType(Method method) {
        if (Long.class.isAssignableFrom(functionalInnerType)) {
            return;
        }
        throw new IllegalStateException("Unsupported ReadResult inner type for count-derived query: "
                + functionalInnerType.getName()
                + " in method "
                + method.toGenericString()
                + ". Supported inner type is java.lang.Long.");
    }

    private void validateReadExistsInnerType(Method method) {
        if (Boolean.class.isAssignableFrom(functionalInnerType)) {
            return;
        }
        throw new IllegalStateException("Unsupported ReadResult inner type for exists-derived query: "
                + functionalInnerType.getName()
                + " in method "
                + method.toGenericString()
                + ". Supported inner type is java.lang.Boolean.");
    }

    private static Class<?> resolveFunctionalInnerType(Method method) {
        Type genericReturnType = method.getGenericReturnType();
        if (genericReturnType instanceof ParameterizedType pt) {
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0) {
                Type innerType = typeArgs[0];
                if (innerType instanceof ParameterizedType innerPt) {
                    return (Class<?>) innerPt.getRawType();
                }
                if (innerType instanceof Class<?> clazz) {
                    return clazz;
                }
            }
        }
        return Object.class;
    }

    @Override
    public Object execute(Object[] parameters) {
        Object[] args = parameters != null ? parameters : new Object[0];
        QueryRuntimeParams runtimeParams = queryRuntimeParams(args);
        try {
            ExecutableQuery<T> executable = dslMapper.map(descriptor, args, runtimeParams);
            Object rawResult = executable.execute(executionResources);
            if (readFunctionalMode) {
                return adaptReturnTypeReadFunctional(rawResult, args);
            }
            if (writeFunctionalMode) {
                return adaptReturnTypeWriteFunctional(rawResult, args);
            }
            return adaptReturnType(rawResult, args);
        } catch (DataAccessException e) {
            if (readFunctionalMode) {
                return new Error<>(
                        "Read operation failed while executing derived query method '" + queryMethod.getName() + "'.",
                        e);
            }
            throw e;
        }
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

    // ---- Functional return-type adaptation ----

    @SuppressWarnings("unchecked")
    private Object adaptReturnTypeReadFunctional(Object rawResult, Object[] args) {
        if (rawResult instanceof Long || rawResult instanceof Boolean) {
            return new Found<>(rawResult);
        }
        if (rawResult instanceof Integer affected) {
            return new Found<>(adaptDeleteResultReadFunctional(affected));
        }
        List<T> results = (List<T>) rawResult;
        return adaptSelectResultReadFunctional(results, args);
    }

    private Object adaptDeleteResultReadFunctional(int affected) {
        if (Long.class.isAssignableFrom(functionalInnerType)) {
            return (long) affected;
        }
        if (Integer.class.isAssignableFrom(functionalInnerType)) {
            return affected;
        }
        if (Boolean.class.isAssignableFrom(functionalInnerType)) {
            return affected > 0;
        }
        throw new IllegalStateException("Unsupported ReadResult inner type for delete-derived query: "
                + functionalInnerType.getName()
                + " in method "
                + queryMethod.getName()
                + ". Supported inner types are java.lang.Long, java.lang.Integer and java.lang.Boolean.");
    }

    private Object adaptSelectResultReadFunctional(List<T> results, Object[] args) {
        if (Page.class.isAssignableFrom(functionalInnerType)) {
            return new Found<>(adaptAsPage(results, args));
        }
        if (Slice.class.isAssignableFrom(functionalInnerType)) {
            return new Found<>(adaptAsSlice(results, args));
        }
        if (List.class.isAssignableFrom(functionalInnerType)
                || Collection.class.isAssignableFrom(functionalInnerType)
                || Iterable.class.isAssignableFrom(functionalInnerType)) {
            return new Found<>(results);
        }
        if (Stream.class.isAssignableFrom(functionalInnerType)) {
            return new Found<>(results.stream());
        }
        if (results.isEmpty()) {
            return new NotFound<>();
        }
        return new Found<>(results.get(0));
    }

    @SuppressWarnings("unchecked")
    private Object adaptReturnTypeWriteFunctional(Object rawResult, Object[] args) {
        if (rawResult instanceof Long || rawResult instanceof Boolean) {
            return new Success<>(rawResult);
        }
        if (rawResult instanceof Integer affected) {
            return new Success<>(adaptDeleteResultWriteFunctional(affected));
        }
        List<T> results = (List<T>) rawResult;
        if (Page.class.isAssignableFrom(functionalInnerType)) {
            return new Success<>(adaptAsPage(results, args));
        }
        if (Slice.class.isAssignableFrom(functionalInnerType)) {
            return new Success<>(adaptAsSlice(results, args));
        }
        if (List.class.isAssignableFrom(functionalInnerType)
                || Collection.class.isAssignableFrom(functionalInnerType)
                || Iterable.class.isAssignableFrom(functionalInnerType)) {
            return new Success<>(results);
        }
        if (Stream.class.isAssignableFrom(functionalInnerType)) {
            return new Success<>(results.stream());
        }
        throw new IllegalStateException("Unsupported WriteResult inner type for find-derived query: "
                + functionalInnerType.getName()
                + " in method "
                + queryMethod.getName()
                + ".");
    }

    private Object adaptDeleteResultWriteFunctional(int affected) {
        if (Long.class.isAssignableFrom(functionalInnerType)) {
            return (long) affected;
        }
        if (Integer.class.isAssignableFrom(functionalInnerType)) {
            return affected;
        }
        if (Boolean.class.isAssignableFrom(functionalInnerType)) {
            return affected > 0;
        }
        throw new IllegalStateException("Unsupported WriteResult inner type for delete-derived query: "
                + functionalInnerType.getName()
                + " in method "
                + queryMethod.getName()
                + ". Supported inner types are java.lang.Long, java.lang.Integer and java.lang.Boolean.");
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

        ExecutableQuery<T> countQuery = dslMapper.map(countDescriptor, args, QueryRuntimeParams.empty());
        long total = (long) countQuery.execute(executionResources);

        return new PageImpl<>(content, effectivePageable, total);
    }

    private Slice<T> adaptAsSlice(List<T> content, Object[] args) {
        Pageable pageable = extractPageable(args);
        Pageable effectivePageable = pageable != null ? pageable : Pageable.unpaged();
        return new SliceImpl<>(content, effectivePageable, false);
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

    private QueryRuntimeParams queryRuntimeParams(Object[] args) {
        Sort runtimeSort = sort(args);
        List<OrderByClause> sortClauses = orderByClauses(runtimeSort);
        PageWindow pageWindow = pageWindow(args);
        return new QueryRuntimeParams(sortClauses, pageWindow);
    }

    private Sort sort(Object[] args) {
        if (descriptor.pageableParamIndex() >= 0 && args.length > descriptor.pageableParamIndex()) {
            Object arg = args[descriptor.pageableParamIndex()];
            if (arg instanceof Pageable p) {
                return p.getSort();
            }
        }
        if (descriptor.sortParamIndex() >= 0 && args.length > descriptor.sortParamIndex()) {
            Object arg = args[descriptor.sortParamIndex()];
            if (arg instanceof Sort s) {
                return s;
            }
        }
        return null;
    }

    private List<OrderByClause> orderByClauses(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return List.of();
        }
        List<OrderByClause> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = columnName(order.getProperty());
            Sorting.SortOrder direction = order.isAscending() ? Sorting.SortOrder.ASC : Sorting.SortOrder.DESC;
            clauses.add(new OrderByClause(column, direction));
        }
        return clauses;
    }

    private PageWindow pageWindow(Object[] args) {
        Pageable pageable = extractPageable(args);
        if (pageable != null && pageable.isPaged()) {
            return new PageWindow(pageable.getPageSize(), pageable.getOffset());
        }
        return null;
    }

    private String columnName(String propertyOrColumn) {
        try {
            return metadataProvider.resolveColumn(propertyOrColumn);
        } catch (IllegalArgumentException e) {
            return propertyOrColumn;
        }
    }
}
