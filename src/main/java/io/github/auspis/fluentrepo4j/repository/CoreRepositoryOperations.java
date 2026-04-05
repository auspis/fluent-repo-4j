package io.github.auspis.fluentrepo4j.repository;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.DslTypeDispatcher;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityWriter;
import io.github.auspis.fluentrepo4j.mapping.helper.SortClauseHelper;
import io.github.auspis.fluentrepo4j.mapping.helper.SortClauseHelper.ColumnOrder;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.select.OrderByBuilder;
import io.github.auspis.fluentsql4j.dsl.select.SelectBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

/**
 * Core JDBC operations shared by {@link FluentRepository} and
 * {@link FunctionalFluentRepository}.
 *
 * <p>Encapsulates all low-level database interactions: query building via fluent-sql-4j,
 * entity mapping, connection management, and SQL exception translation. This class is
 * agnostic to the result-wrapping strategy used by the calling repository facade.
 *
 * <p>Package-private by design — not part of the public API.
 *
 * @param <T>  the entity type
 * @param <ID> the entity identifier type
 */
class CoreRepositoryOperations<T, ID> {

    private final FluentEntityInformation<T, ID> entityInformation;
    private final FluentConnectionProvider connectionProvider;
    private final DSL dsl;
    private final FluentEntityRowMapper<T> rowMapper;
    private final FluentEntityWriter<T> entityWriter;
    private final SQLExceptionTranslator exceptionTranslator;
    private final SaveDecisionResolver<T, ID> saveDecisionResolver;
    private final SortClauseHelper sortClauseHelper;

    CoreRepositoryOperations(
            FluentEntityInformation<T, ID> entityInformation, FluentConnectionProvider connectionProvider, DSL dsl) {
        this.entityInformation = entityInformation;
        this.connectionProvider = connectionProvider;
        this.dsl = dsl;
        this.rowMapper = new FluentEntityRowMapper<>(entityInformation);
        this.entityWriter = new FluentEntityWriter<>(entityInformation);
        this.exceptionTranslator = new SQLExceptionSubclassTranslator();
        this.saveDecisionResolver = new SaveDecisionResolver<>(entityInformation, this::existsByIdRaw);
        this.sortClauseHelper = new SortClauseHelper(entityInformation);
    }

    // ---- Accessors for facade-specific logic ----

    SaveDecisionResolver<T, ID> getSaveDecisionResolver() {
        return saveDecisionResolver;
    }

    ID getEntityId(T entity) {
        return entityInformation.getId(entity);
    }

    // ---- Find ----

    Optional<T> findByIdRaw(ID id) {
        String table = entityInformation.getTableName();
        String idColumn = entityInformation.getIdColumnName();
        Connection conn = connectionProvider.getConnection();
        try {
            PreparedStatement ps = DslTypeDispatcher.eq(
                            dsl.selectAll().from(table).where().column(idColumn), id)
                    .build(conn);
            try (ps;
                    ResultSet rs = ps.executeQuery()) {
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

    boolean existsByIdRaw(ID id) {
        String table = entityInformation.getTableName();
        String idColumn = entityInformation.getIdColumnName();
        Connection conn = connectionProvider.getConnection();
        try {
            PreparedStatement ps = DslTypeDispatcher.eq(
                            dsl.select().countStar().from(table).where().column(idColumn), id)
                    .build(conn);
            try (ps;
                    ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw translateException("existsById", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    long countRaw() {
        String table = entityInformation.getTableName();
        Connection conn = connectionProvider.getConnection();
        try {
            PreparedStatement ps = dsl.select().countStar().from(table).build(conn);
            try (ps;
                    ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw translateException("count", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    List<T> findAllRaw(Sort sort, Pageable pageable) {
        String table = entityInformation.getTableName();
        List<ColumnOrder> orders = sortClauseHelper.resolve(sort);
        Connection conn = connectionProvider.getConnection();
        try {
            SelectBuilder selectBuilder = dsl.selectAll().from(table);

            if (pageable != null) {
                selectBuilder.fetch(pageable.getPageSize()).offset(pageable.getOffset());
            }

            PreparedStatement ps;
            if (orders.isEmpty()) {
                ps = selectBuilder.build(conn);
            } else {
                OrderByBuilder orderByBuilder = selectBuilder.orderBy();
                for (ColumnOrder order : orders) {
                    orderByBuilder = order.direction() == Sort.Direction.ASC
                            ? orderByBuilder.asc(order.columnName())
                            : orderByBuilder.desc(order.columnName());
                }
                ps = orderByBuilder.build(conn);
            }
            return executeAndMapRows(ps);
        } catch (SQLException e) {
            throw translateException("findAll", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    // ---- Delete ----

    int deleteByIdRaw(ID id) {
        String table = entityInformation.getTableName();
        String idColumn = entityInformation.getIdColumnName();
        Connection conn = connectionProvider.getConnection();
        try {
            PreparedStatement ps = DslTypeDispatcher.eq(
                            dsl.deleteFrom(table).where().column(idColumn), id)
                    .build(conn);
            try (ps) {
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw translateException("deleteById", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    long deleteAllRaw() {
        String table = entityInformation.getTableName();
        Connection conn = connectionProvider.getConnection();
        try (PreparedStatement ps = dsl.deleteFrom(table).build(conn)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw translateException("deleteAll", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    // ---- Save ----

    <S extends T> S insertWithProvidedId(S entity) {
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

    @SuppressWarnings("unchecked")
    <S extends T> S insertWithIdentity(S entity) {
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

    <S extends T> S update(S entity) {
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
            try (PreparedStatement ps =
                    DslTypeDispatcher.eq(updateWhere, idValue).build(conn)) {
                int rowCount = ps.executeUpdate();
                if (rowCount == 0) {
                    throw new OptimisticLockingFailureException(
                            "Entity " + entity.getClass().getSimpleName()
                                    + " with ID " + idValue + " was not found for update."
                                    + " It may have been deleted by another transaction.");
                }
            }
            return entity;
        } catch (SQLException e) {
            throw translateException("update", e);
        } finally {
            connectionProvider.releaseConnection(conn);
        }
    }

    // ---- Internal ----

    private List<T> executeAndMapRows(PreparedStatement ps) throws SQLException {
        try (ps;
                ResultSet rs = ps.executeQuery()) {
            List<T> results = new ArrayList<>();
            int rowNum = 0;
            while (rs.next()) {
                results.add(rowMapper.mapRow(rs, rowNum++));
            }
            return results;
        }
    }

    private DataAccessException translateException(String task, SQLException ex) {
        DataAccessException translated = exceptionTranslator.translate(task, ex.getMessage(), ex);
        return translated != null ? translated : new org.springframework.jdbc.UncategorizedSQLException(task, null, ex);
    }
}
