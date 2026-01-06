package io.github.auspis.repo4j.core;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Interfaccia funzionale per mappare una riga di ResultSet a un oggetto di dominio.
 *
 * @param <T> il tipo dell'oggetto di dominio
 */
@FunctionalInterface
public interface RowMapper<T> {
    /**
     * Mappa una riga del ResultSet a un oggetto di tipo T.
     *
     * @param rs il ResultSet posizionato sulla riga da mappare
     * @return l'oggetto mappato
     * @throws SQLException se si verifica un errore durante l'estrazione dei dati
     */
    T mapRow(ResultSet rs) throws SQLException;
}
