package io.github.auspis.repo4j.example;

import io.github.auspis.repo4j.core.BaseRepository;
import io.github.auspis.repo4j.core.RowMapper;
import io.github.auspis.repo4j.core.provider.ConnectionProvider;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Concrete repository for the User entity.
 * Demonstrates how to extend BaseRepository and implement ResultSet ↔ User mapping.
 */
public class UserRepository extends BaseRepository<User, Long> {

    private static final RowMapper<User> USER_MAPPER = rs -> new User(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email")
    );

    /**
     * Schema for the users table (execute once on a new database):
     * CREATE TABLE users (
     *     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     *     name VARCHAR(255) NOT NULL,
     *     email VARCHAR(255) UNIQUE NOT NULL
     * );
     */

    /**
     * Constructs a UserRepository with the specified ConnectionProvider.
     *
     * @param connectionProvider the provider managing Connection lifecycle
     */
    public UserRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    /**
     * Creates a new user in the database.
     *
     * @param user the user to create
     * @return the created user with generated ID
     */
    @Override
    public User create(User user) {
        String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
        Long generatedId = executeInsertWithGeneratedKey(
                sql,
                rs -> rs.getLong(1),
                user.getName(),
                user.getEmail()
        );
        user.setId(generatedId);
        return user;
    }

    /**
     * Retrieves a user by ID.
     *
     * @param id the user ID
     * @return Optional containing the user if found
     */
    @Override
    public Optional<User> findById(Long id) {
        String sql = "SELECT id, name, email FROM users WHERE id = ?";
        return executeQuerySingle(sql, USER_MAPPER, id);
    }

    /**
     * Retrieves all users.
     *
     * @return list of all users
     */
    @Override
    public List<User> findAll() {
        String sql = "SELECT id, name, email FROM users ORDER BY id";
        return executeQuery(sql, USER_MAPPER);
    }

    /**
     * Updates an existing user.
     *
     * @param user the user with updated data
     * @return the updated user
     */
    @Override
    public User update(User user) {
        String sql = "UPDATE users SET name = ?, email = ? WHERE id = ?";
        int updated = executeUpdate(sql, user.getName(), user.getEmail(), user.getId());
        if (updated == 0) {
            throw new RuntimeException("User with ID " + user.getId() + " not found");
        }
        return user;
    }

    /**
     * Deletes a user by ID.
     *
     * @param id the ID of the user to delete
     */
    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        executeUpdate(sql, id);
    }

    /**
     * Finds a user by email.
     *
     * @param email the user email
     * @return Optional containing the user if found
     */
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT id, name, email FROM users WHERE email = ?";
        return executeQuerySingle(sql, USER_MAPPER, email);
    }

    /**
     * Finds all users with a specific name.
     *
     * @param name the name to search for (uses pattern matching)
     * @return list of users containing the name
     */
    public List<User> findByNameLike(String name) {
        String sql = "SELECT id, name, email FROM users WHERE name LIKE ? ORDER BY id";
        return executeQuery(sql, USER_MAPPER, "%" + name + "%");
    }
}
