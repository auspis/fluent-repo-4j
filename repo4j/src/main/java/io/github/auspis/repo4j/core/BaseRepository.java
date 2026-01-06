package io.github.auspis.repo4j.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Classe base astratta per repository JDBC puro.
 * Gestisce le operazioni CRUD comuni, con Connection recuperata da ConnectionProvider.
 *
 * @param <T>  il tipo dell'entità gestita dal repository
 * @param <ID> il tipo dell'identificatore univoco
 */
public abstract class BaseRepository<T, ID> {

    /**
     * Recupera la Connection dal provider.
     *
     * @return la Connection del thread corrente
     */
    protected final Connection getConnection() {
        return ConnectionProvider.getConnection();
    }

    /**
     * Crea un nuovo record nel database.
     *
     * @param entity l'entità da creare
     * @return l'entità creata (potenzialmente con ID generato)
     * @throws RepositoryException se si verifica un errore SQL
     */
    public abstract T create(T entity);

    /**
     * Recupera un record per ID.
     *
     * @param id l'identificatore univoco
     * @return un Optional contenente l'entità se trovata
     * @throws RepositoryException se si verifica un errore SQL
     */
    public abstract Optional<T> findById(ID id);

    /**
     * Recupera tutti i record.
     *
     * @return una lista di tutte le entità
     * @throws RepositoryException se si verifica un errore SQL
     */
    public abstract List<T> findAll();

    /**
     * Aggiorna un record esistente.
     *
     * @param entity l'entità con i dati aggiornati
     * @return l'entità aggiornata
     * @throws RepositoryException se si verifica un errore SQL
     */
    public abstract T update(T entity);

    /**
     * Cancella un record per ID.
     *
     * @param id l'identificatore univoco
     * @throws RepositoryException se si verifica un errore SQL
     */
    public abstract void delete(ID id);

    /**
     * Helper per eseguire una query SELECT e mappare il risultato a una lista di entità.
     *
     * @param sql        la query SQL
     * @param mapper     il RowMapper per mappare i risultati
     * @param parameters i parametri della query (in ordine)
     * @return lista di entità mappate
     * @throws RepositoryException se si verifica un errore SQL
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
            throw new RepositoryException("Errore durante l'esecuzione della query: " + sql, e);
        }
        return result;
    }

    /**
     * Helper per eseguire una query SELECT e mappare un singolo risultato.
     *
     * @param sql        la query SQL
     * @param mapper     il RowMapper per mappare il risultato
     * @param parameters i parametri della query (in ordine)
     * @return Optional contenente l'entità mappata se presente
     * @throws RepositoryException se si verifica un errore SQL
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
            throw new RepositoryException("Errore durante l'esecuzione della query: " + sql, e);
        }
        return Optional.empty();
    }

    /**
     * Helper per eseguire un INSERT, UPDATE o DELETE.
     *
     * @param sql        la query SQL
     * @param parameters i parametri della query (in ordine)
     * @return il numero di righe affette
     * @throws RepositoryException se si verifica un errore SQL
     */
    protected final int executeUpdate(String sql, Object... parameters) {
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            setParameters(stmt, parameters);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante l'esecuzione dell'update: " + sql, e);
        }
    }

    /**
     * Helper per eseguire un INSERT con generazione di chiave primaria.
     *
     * @param sql        la query SQL
     * @param mapper     il RowMapper per mappare la chiave generata
     * @param parameters i parametri della query (in ordine)
     * @return la chiave generata mappata
     * @throws RepositoryException se si verifica un errore SQL
     */
    protected final <K> K executeInsertWithGeneratedKey(String sql, RowMapper<K> mapper, Object... parameters) {
        try (PreparedStatement stmt = getConnection().prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            setParameters(stmt, parameters);
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return mapper.mapRow(generatedKeys);
                } else {
                    throw new RepositoryException("Nessuna chiave generata");
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante l'esecuzione dell'insert con chiave generata: " + sql, e);
        }
    }

    /**
     * Helper per impostare i parametri di un PreparedStatement.
     *
     * @param stmt       il PreparedStatement
     * @param parameters i parametri in ordine
     * @throws SQLException se si verifica un errore durante l'impostazione
     */
    private void setParameters(PreparedStatement stmt, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            stmt.setObject(i + 1, parameters[i]);
        }
    }
}
