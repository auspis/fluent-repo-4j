package io.github.auspis.repo4j.core;

import io.github.auspis.repo4j.core.provider.ConnectionProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for JDBC repositories.
 * Manages common CRUD operations with Connection retrieved from the injected ConnectionProvider.
 *
 * @param <T>  the type of the entity managed by the repository
 * @param <ID> the type of the unique identifier
 */
public abstract class BaseRepository<T, ID> {

    private final ConnectionProvider connectionProvider;

    /**
     * Constructs a BaseRepository with the specified ConnectionProvider.
     *
     * @param connectionProvider the provider managing Connection lifecycle
     */
    public BaseRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Retrieves the Connection from the provider.
     *
     * @return the Connection for the current context
     */
    protected final Connection getConnection() {
        return connectionProvider.getConnection();
    }

    /**
     * Creates a new record in the database.
     *
     * @param entity the entity to create
     * @return the created entity (potentially with generated ID)
     * @throws RepositoryException if an SQL error occurs
     */
    public abstract T create(T entity);

    /**
     * Retrieves a record by ID.
     *
     * @param id the unique identifier
     * @return an Optional containing the entity if found
     * @throws RepositoryException if an SQL error occurs
     */
    public abstract Optional<T> findById(ID id);

    /**
     * Retrieves all records.
     *
     * @return a list of all entities
     * @throws RepositoryException if an SQL error occurs
     */
    public abstract List<T> findAll();

    /**
     * Updates an existing record.
     *
     * @param entity the entity with updated data
     * @return the updated entity
     * @throws RepositoryException if an SQL error occurs
     */
    public abstract T update(T entity);

    /**
     * Deletes a record by ID.
     *
     * @param id the unique identifier
     * @throws RepositoryException if an SQL error occurs
     */
    public abstract void delete(ID id);

    /**
     * Helper to execute a SELECT query and map the result to a list of entities.
     *
     * @param sql        the SQL query
     * @param mapper     the RowMapper to map results
     * @param parameters the query parameters (in order)
     * @return list of mapped entities
     * @throws RepositoryException if an SQL error occurs
     */
    protected final List<T> executeQuery(String sql, RowMapper<T> mapper, Object... parameters) {
        List<T> result = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            setParameters(stmt, parameters);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapper.mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Error executing query: " + sql, e);
        }
        return result;
    }

    /**
     * Helper to execute a SELECT query and map a single result.
     *
     * @param sql        the SQL query
     * @param mapper     the RowMapper to map the result
     * @param parameters the query parameters (in order)
     * @return Optional containing the mapped entity if present
     * @throws RepositoryException if an SQL error occurs
     */
    protected final Optional<T> executeQuerySingle(String sql, RowMapper<T> mapper, Object... parameters) {
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            setParameters(stmt, parameters);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapper.mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Error executing query: " + sql, e);
        }
        return Optional.empty();
    }

    /**
     * Helper to execute an INSERT, UPDATE, or DELETE.
     *
     * @param sql        the SQL query
     * @param parameters the query parameters (in order)
     * @return the number of affected rows
     * @throws RepositoryException if an SQL error occurs
     */
    protected final int executeUpdate(String sql, Object... parameters) {
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            setParameters(stmt, parameters);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Error executing update: " + sql, e);
        }
    }

    /**
     * Helper to execute an INSERT with generated key retrieval.
     *
     * @param sql        the SQL query
     * @param mapper     the RowMapper to map the generated key
     * @param parameters the query parameters (in order)
     * @return the generated key mapped
     * @throws RepositoryException if an SQL error occurs
     */
    protected final <K> K executeInsertWithGeneratedKey(String sql, RowMapper<K> mapper, Object... parameters) {
        try (PreparedStatement stmt = getConnection().prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            setParameters(stmt, parameters);
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return mapper.mapRow(generatedKeys);
                } else {
                    throw new RepositoryException("No generated key returned");
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Error executing insert with generated key: " + sql, e);
        }
    }

    /**
     * Helper to set parameters for a PreparedStatement.
     *
     * @param stmt       the PreparedStatement
     * @param parameters the parameters in order
     * @throws SQLException if an error occurs during parameter setting
     */
    private void setParameters(PreparedStatement stmt, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            stmt.setObject(i + 1, parameters[i]);
        }
    }
}
