package io.github.auspis.repo4j.example;

import io.github.auspis.repo4j.core.BaseRepository;
import io.github.auspis.repo4j.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Repository concreto per l'entità User.
 * Dimostra come estendere BaseRepository e implementare il mapping ResultSet ↔ User.
 */
public class UserRepository extends BaseRepository<User, Long> {

    private static final RowMapper<User> USER_MAPPER = rs -> new User(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email")
    );

    /**
     * Schema della tabella users (eseguire una volta su un nuovo database):
     * CREATE TABLE users (
     *     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     *     name VARCHAR(255) NOT NULL,
     *     email VARCHAR(255) UNIQUE NOT NULL
     * );
     */

    /**
     * Crea un nuovo utente nel database.
     *
     * @param user l'utente da creare
     * @return l'utente creato con ID generato
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
     * Recupera un utente per ID.
     *
     * @param id l'ID dell'utente
     * @return Optional contenente l'utente se trovato
     */
    @Override
    public Optional<User> findById(Long id) {
        String sql = "SELECT id, name, email FROM users WHERE id = ?";
        return executeQuerySingle(sql, USER_MAPPER, id);
    }

    /**
     * Recupera tutti gli utenti.
     *
     * @return lista di tutti gli utenti
     */
    @Override
    public List<User> findAll() {
        String sql = "SELECT id, name, email FROM users ORDER BY id";
        return executeQuery(sql, USER_MAPPER);
    }

    /**
     * Aggiorna un utente esistente.
     *
     * @param user l'utente con i dati aggiornati
     * @return l'utente aggiornato
     */
    @Override
    public User update(User user) {
        String sql = "UPDATE users SET name = ?, email = ? WHERE id = ?";
        int updated = executeUpdate(sql, user.getName(), user.getEmail(), user.getId());
        if (updated == 0) {
            throw new RuntimeException("Utente con ID " + user.getId() + " non trovato");
        }
        return user;
    }

    /**
     * Cancella un utente per ID.
     *
     * @param id l'ID dell'utente da cancellare
     */
    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        executeUpdate(sql, id);
    }

    /**
     * Trova un utente per email.
     *
     * @param email l'email dell'utente
     * @return Optional contenente l'utente se trovato
     */
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT id, name, email FROM users WHERE email = ?";
        return executeQuerySingle(sql, USER_MAPPER, email);
    }

    /**
     * Trova tutti gli utenti con un nome specifico.
     *
     * @param name il nome da cercare (usa pattern matching)
     * @return lista di utenti che contengono il nome
     */
    public List<User> findByNameLike(String name) {
        String sql = "SELECT id, name, email FROM users WHERE name LIKE ? ORDER BY id";
        return executeQuery(sql, USER_MAPPER, "%" + name + "%");
    }
}
