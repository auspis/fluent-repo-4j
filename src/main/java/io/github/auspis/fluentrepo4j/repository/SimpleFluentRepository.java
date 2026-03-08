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
import io.github.auspis.fluentrepo4j.mapping.IdGenerationStrategy;
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
        rowMapper = new FluentEntityRowMapper<>(entityInformation);
        entityWriter = new FluentEntityWriter<>(entityInformation);
        exceptionTranslator = new SQLExceptionSubclassTranslator();
    }

    // ---- Save ----

    /**
     * Saves the given entity, performing an INSERT or UPDATE depending on whether
     * the entity is considered new.
     * <p>
     * The new/existing determination follows this logic:
     * <ol>
     *   <li>If the entity implements {@link org.springframework.data.domain.Persistable},
     *       delegates to {@code entity.isNew()} (developer has full control).</li>
     *   <li>Otherwise, uses Spring Data's default: ID == null → new.</li>
     *   <li>As a safety fallback, if {@code isNew()} returns {@code false} (ID is non-null)
     *       but the entity does not actually exist in the database, treats it as a new entity
     *       and performs an INSERT. This handles the case where the application provides
     *       the ID upfront (PROVIDED strategy) without implementing {@code Persistable}.</li>
     * </ol>
     */
    @Override
    public <S extends T> S save(S entity) {
        if (entityInformation.isNew(entity)) {
            return insert(entity);
        }
        // Fallback: isNew() returned false (ID is non-null), but verify in the DB
        ID id = entityInformation.getId(entity);
        if (id != null && !existsById(id)) {
            return insert(entity);
        }
        return update(entity);
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
                boolean result = rs.getLong(1) > 0;
                System.out.println("existsById: Checking existence of ID " + id + " in table " + table + " → " + result);
                return result;
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

    @SuppressWarnings("unchecked")
    private <S extends T> S insert(S entity) {
        IdGenerationStrategy strategy = entityInformation.getIdGenerationStrategy();
        if (strategy == IdGenerationStrategy.PROVIDED) {
            return insertWithProvidedId(entity);
        }
        return insertWithIdentity(entity);
    }

    /**
     * Inserts an entity whose ID was set by the application (PROVIDED strategy).
     * Validates that the ID is non-null before proceeding.
     */
    private <S extends T> S insertWithProvidedId(S entity) {
        ID idValue = entityInformation.getId(entity);
        if (idValue == null) {
            throw new IllegalArgumentException(
                    "Entity " + entity.getClass().getSimpleName() + " requires ID to be set before save(). "
                            + "No @GeneratedValue found; use application-provided ID strategy, "
                            + "or annotate the @Id field with @GeneratedValue(strategy = GenerationType.IDENTITY).");
        }
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
            return entity;
        } catch (SQLException e) {
            throw translateException("insert", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    /**
     * Inserts an entity whose ID is auto-generated by the database (IDENTITY strategy).
     * Excludes the ID column from the INSERT and reads the generated key after execution.
     */
    @SuppressWarnings("unchecked")
    private <S extends T> S insertWithIdentity(S entity) {
        String table = entityInformation.getTableName();
        Map<String, Object> values = entityWriter.getNonIdColumnValues(entity);
        Connection conn = connectionProvider.getConnection();
        try {
            var builder = dsl.insertInto(table);
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                builder = DslTypeDispatcher.set(builder, entry.getKey(), entry.getValue());
            }
            try (PreparedStatement ps = builder.build(conn)) {
                ps.executeUpdate();
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        ID generatedId = (ID) generatedKeys.getObject(1);
                        entityInformation.setId(entity, generatedId);
                    }
                }
            }
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
        DataAccessException translated = exceptionTranslator.translate("SimpleFluentRepository." + task, ex.getMessage(), ex);
        return translated != null ? translated : new org.springframework.jdbc.UncategorizedSQLException(
                "SimpleFluentRepository." + task, null, ex);
    }
}
