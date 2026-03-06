package io.github.auspis.fluentrepo4j.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.DslTypeDispatcher;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityWriter;
import io.github.auspis.fluentsql4j.dsl.DSL;

/**
 * Default implementation of {@link CrudRepository} using fluent-sql-4j for SQL generation
 * and Spring's {@link org.springframework.jdbc.datasource.DataSourceUtils} for connection management.
 *
 * @param <T>  the entity type
 * @param <ID> the entity identifier type
 */
public class SimpleFluentRepository<T, ID> implements CrudRepository<T, ID> {

    private final FluentEntityInformation<T, ID> entityInformation;
    private final FluentConnectionProvider connectionProvider;
    private final DSL dsl;
    private final FluentEntityRowMapper<T> rowMapper;
    private final FluentEntityWriter<T> entityWriter;
    private final SQLExceptionTranslator exceptionTranslator;

    public SimpleFluentRepository(FluentEntityInformation<T, ID> entityInformation,
                                  FluentConnectionProvider connectionProvider,
                                  DSL dsl) {
        this.entityInformation = entityInformation;
        this.connectionProvider = connectionProvider;
        this.dsl = dsl;
        this.rowMapper = new FluentEntityRowMapper<>(entityInformation);
        this.entityWriter = new FluentEntityWriter<>(entityInformation);
        this.exceptionTranslator = new SQLExceptionSubclassTranslator();
    }

    // ---- Save ----

    @Override
    public <S extends T> S save(S entity) {
        if (entityInformation.isNew(entity)) {
            return insert(entity);
        } else {
            return update(entity);
        }
    }

    @Override
    public <S extends T> Iterable<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    // ---- Find ----

    @Override
    public Optional<T> findById(ID id) {
        String table = entityInformation.getTableName();
        String idColumn = entityInformation.getIdColumnName();
        Connection conn = connectionProvider.getConnection();
        try {
            var where = dsl.selectAll()
                    .from(table)
                    .where().column(idColumn);
            PreparedStatement ps = DslTypeDispatcher.eq(where, id)
                    .build(conn);
            try (ps; ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rowMapper.mapRow(rs, 0));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw translateException("findById", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    @Override
    public boolean existsById(ID id) {
        String table = entityInformation.getTableName();
        String idColumn = entityInformation.getIdColumnName();
        Connection conn = connectionProvider.getConnection();
        try {
            var where = dsl.select()
                    .countStar()
                    .from(table)
                    .where().column(idColumn);
            PreparedStatement ps = DslTypeDispatcher.eq(where, id)
                    .build(conn);
            try (ps; ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw translateException("existsById", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    @Override
    public Iterable<T> findAll() {
        String table = entityInformation.getTableName();
        Connection conn = connectionProvider.getConnection();
        try {
            PreparedStatement ps = dsl.selectAll()
                    .from(table)
                    .build(conn);
            try (ps; ResultSet rs = ps.executeQuery()) {
                List<T> results = new ArrayList<>();
                int rowNum = 0;
                while (rs.next()) {
                    results.add(rowMapper.mapRow(rs, rowNum++));
                }
                return results;
            }
        } catch (SQLException e) {
            throw translateException("findAll", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    @Override
    public Iterable<T> findAllById(Iterable<ID> ids) {
        List<T> results = new ArrayList<>();
        for (ID id : ids) {
            findById(id).ifPresent(results::add);
        }
        return results;
    }

    @Override
    public long count() {
        String table = entityInformation.getTableName();
        Connection conn = connectionProvider.getConnection();
        try {
            PreparedStatement ps = dsl.select()
                    .countStar()
                    .from(table)
                    .build(conn);
            try (ps; ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw translateException("count", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    // ---- Delete ----

    @Override
    public void deleteById(ID id) {
        String table = entityInformation.getTableName();
        String idColumn = entityInformation.getIdColumnName();
        Connection conn = connectionProvider.getConnection();
        try {
            var where = dsl.deleteFrom(table)
                    .where().column(idColumn);
            PreparedStatement ps = DslTypeDispatcher.eq(where, id)
                    .build(conn);
            try (ps) {
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw translateException("deleteById", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    @Override
    public void delete(T entity) {
        ID id = entityInformation.getId(entity);
        if (id == null) {
            throw new IllegalArgumentException("Cannot delete entity with null ID");
        }
        deleteById(id);
    }

    @Override
    public void deleteAllById(Iterable<? extends ID> ids) {
        for (ID id : ids) {
            deleteById(id);
        }
    }

    @Override
    public void deleteAll(Iterable<? extends T> entities) {
        for (T entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAll() {
        String table = entityInformation.getTableName();
        Connection conn = connectionProvider.getConnection();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw translateException("deleteAll", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    // ---- Internal ----

    private <S extends T> S insert(S entity) {
        String table = entityInformation.getTableName();
        Map<String, Object> values = entityWriter.getAllColumnValues(entity);
        Connection conn = connectionProvider.getConnection();
        try {
            var builder = dsl.insertInto(table);
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                builder = DslTypeDispatcher.set(builder, entry.getKey(), entry.getValue());
            }
            try (PreparedStatement ps = builder.build(conn)) {
                ps.executeUpdate();
            }
            
            // For now, simply return the entity without ID
            // TODO: Implement proper ID generation and retrieval support
            return entity;
        } catch (SQLException e) {
            throw translateException("insert", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    private <S extends T> S update(S entity) {
        String table = entityInformation.getTableName();
        String idColumn = entityInformation.getIdColumnName();
        ID idValue = entityInformation.getId(entity);
        Map<String, Object> nonIdValues = entityWriter.getNonIdColumnValues(entity);
        Connection conn = connectionProvider.getConnection();
        try {
            var builder = dsl.update(table);
            for (Map.Entry<String, Object> entry : nonIdValues.entrySet()) {
                builder = DslTypeDispatcher.set(builder, entry.getKey(), entry.getValue());
            }
            var updateWhere = builder.where().column(idColumn);
            try (PreparedStatement ps = DslTypeDispatcher.eq(updateWhere, idValue).build(conn)) {
                ps.executeUpdate();
            }
            return entity;
        } catch (SQLException e) {
            throw translateException("update", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    private DataAccessException translateException(String task, SQLException ex) {
        DataAccessException translated = exceptionTranslator.translate("SimpleFluentRepository." + task, null, ex);
        return translated != null ? translated : new org.springframework.jdbc.UncategorizedSQLException(
                "SimpleFluentRepository." + task, null, ex);
    }
}
