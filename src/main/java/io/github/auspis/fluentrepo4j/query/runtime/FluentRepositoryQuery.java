package io.github.auspis.fluentrepo4j.query.runtime;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper;
import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentrepo4j.parse.PartTreeAdapter;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentrepo4j.query.QueryOperation;
import io.github.auspis.fluentrepo4j.query.mapper.dsl.MappedQuery;
import io.github.auspis.fluentrepo4j.query.mapper.dsl.QueryDescriptorToDslMapper;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.dao.DataAccessException;
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
import org.springframework.jdbc.support.SQLExceptionTranslator;

/**
 * {@link RepositoryQuery} implementation for dynamic query methods derived from
 * Spring Data method names (e.g. {@code findByEmailAndName}).
 *
 * <p>Instances are created once per repository method by
 * {@link FluentQueryLookupStrategy} and hold a cached {@link QueryDescriptor}.
 * At execution time ({@link #execute(Object[])}) they build the DSL query,
 * execute it, and map the result to the expected return type.
 *
 * @param <T>  entity type
 * @param <ID> identifier type
 */
public class FluentRepositoryQuery<T, ID> implements RepositoryQuery {

    private final QueryMethod queryMethod;
    private final QueryDescriptor descriptor;
    private final QueryDescriptorToDslMapper<T, ID> dslMapper;
    private final FluentConnectionProvider connectionProvider;
    private final FluentEntityRowMapper<T> rowMapper;
    private final SQLExceptionTranslator exceptionTranslator;

    public FluentRepositoryQuery(
            Method method,
            RepositoryMetadata metadata,
            ProjectionFactory projectionFactory,
            FluentEntityInformation<T, ID> entityInformation,
            FluentConnectionProvider connectionProvider,
            DSL dsl) {
        // TODO: [URGENT] switch to non deprecated QueryMethod constructor once we require Spring Data 3.0+
        this.queryMethod = new QueryMethod(method, metadata, projectionFactory);
        this.connectionProvider = connectionProvider;
        this.rowMapper = new FluentEntityRowMapper<>(entityInformation);
        this.exceptionTranslator = new SQLExceptionSubclassTranslator();

        PropertyMetadataProvider<T, ID> metaProvider = new PropertyMetadataProvider<>(entityInformation);
        this.dslMapper = new QueryDescriptorToDslMapper<>(dsl, metaProvider);

        // Build and cache the descriptor at construction time
        this.descriptor = PartTreeAdapter.adapt(method, metadata.getDomainType());
    }

    @Override
    public Object execute(Object[] parameters) {
        Object[] args = parameters != null ? parameters : new Object[0];
        MappedQuery runner = dslMapper.map(descriptor, args);
        return switch (runner) {
            case MappedQuery.Select sr -> executeSelect(sr, args);
            case MappedQuery.Delete dr -> executeDelete(dr);
        };
    }

    @Override
    public QueryMethod getQueryMethod() {
        return queryMethod;
    }

    // ---- SELECT ----

    private Object executeSelect(MappedQuery.Select sr, Object[] parameters) {
        QueryOperation op = descriptor.operation();

        if (op == QueryOperation.COUNT) {
            return executeCount(sr);
        }
        if (op == QueryOperation.EXISTS) {
            return executeCount(sr) > 0;
        }

        // FIND: choose based on return type
        Class<?> returnType = queryMethod.getReturnedObjectType();

        if (queryMethod.isPageQuery()) {
            return executePageQuery(sr, parameters);
        }
        if (queryMethod.isSliceQuery()) {
            return executeSliceQuery(sr, parameters);
        }

        List<T> results = executeList(sr);

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

    private long executeCount(MappedQuery.Select sr) {
        Connection conn = connectionProvider.getConnection();
        try {
            PreparedStatement ps = sr.buildStatement(conn);
            try (ps;
                    ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw translateException("count/exists", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    private Page<T> executePageQuery(MappedQuery.Select sr, Object[] parameters) {
        // Build a count-only query using the same WHERE clause
        long total = executeCountForPage(parameters);
        Pageable pageable = extractPageable(parameters);

        if (total == 0) {
            return new PageImpl<>(List.of(), pageable != null ? pageable : Pageable.unpaged(), 0);
        }

        List<T> content = executeList(sr);
        return new PageImpl<>(content, pageable != null ? pageable : Pageable.unpaged(), total);
    }

    private Slice<T> executeSliceQuery(MappedQuery.Select sr, Object[] parameters) {
        Pageable pageable = extractPageable(parameters);
        List<T> content = executeList(sr);
        return new SliceImpl<>(content, pageable != null ? pageable : Pageable.unpaged(), !content.isEmpty());
    }

    private long executeCountForPage(Object[] parameters) {
        // Rebuild as a COUNT query using the same WHERE clause (no ORDER BY or FETCH)
        QueryDescriptor countDescriptor = new QueryDescriptor(
                QueryOperation.COUNT,
                descriptor.distinct(),
                null,
                descriptor.predicateDescriptor(),
                List.of(),
                descriptor.pageableParamIndex(),
                descriptor.sortParamIndex());

        MappedQuery countMapped = dslMapper.map(countDescriptor, parameters);
        if (countMapped instanceof MappedQuery.Select csr) {
            return executeCount(csr);
        }
        return 0;
    }

    private List<T> executeList(MappedQuery.Select sr) {
        Connection conn = connectionProvider.getConnection();
        try {
            PreparedStatement ps = sr.buildStatement(conn);
            try (ps;
                    ResultSet rs = ps.executeQuery()) {
                List<T> results = new ArrayList<>();
                int rowNum = 0;
                while (rs.next()) {
                    results.add(rowMapper.mapRow(rs, rowNum++));
                }
                return results;
            }
        } catch (SQLException e) {
            throw translateException("find", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    // ---- DELETE ----

    private Object executeDelete(MappedQuery.Delete dr) {
        Connection conn = connectionProvider.getConnection();
        try {
            PreparedStatement ps = dr.buildStatement(conn);
            try (ps) {
                int affected = ps.executeUpdate();
                Class<?> returnType = queryMethod.getReturnedObjectType();
                if (long.class.equals(returnType) || Long.class.equals(returnType)) {
                    return (long) affected;
                }
                if (int.class.equals(returnType) || Integer.class.equals(returnType)) {
                    return affected;
                }
                return null; // void
            }
        } catch (SQLException e) {
            throw translateException("delete", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    // ---- Helpers ----

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

    private DataAccessException translateException(String task, SQLException ex) {
        DataAccessException translated =
                exceptionTranslator.translate("FluentRepositoryQuery." + task, ex.getMessage(), ex);
        return translated != null
                ? translated
                : new org.springframework.jdbc.UncategorizedSQLException("FluentRepositoryQuery." + task, null, ex);
    }
}
