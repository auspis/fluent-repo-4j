package io.github.auspis.repo4j.core;

import java.sql.Connection;

/**
 * Gestisce il ciclo di vita della Connection per thread.
 * Usa ThreadLocal per mantenere una Connection per thread, permettendo
 * al repository di recuperarla senza riceverla come parametro.
 */
public final class ConnectionProvider {
    private static final ThreadLocal<Connection> CONNECTION = new ThreadLocal<>();

    private ConnectionProvider() {
        // Utility class, non istanziabile
    }

    /**
     * Imposta la Connection per il thread corrente.
     *
     * @param connection la Connection da gestire
     * @throws IllegalArgumentException se connection è null
     */
    public static void setConnection(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection non può essere null");
        }
        CONNECTION.set(connection);
    }

    /**
     * Recupera la Connection per il thread corrente.
     *
     * @return la Connection gestita dal provider
     * @throws IllegalStateException se nessuna Connection è stata impostata
     */
    public static Connection getConnection() {
        Connection conn = CONNECTION.get();
        if (conn == null) {
            throw new IllegalStateException("Nessuna Connection disponibile nel thread corrente. Usa setConnection() prima.");
        }
        return conn;
    }

    /**
     * Verifica se una Connection è disponibile per il thread corrente.
     *
     * @return true se una Connection è impostata, false altrimenti
     */
    public static boolean hasConnection() {
        return CONNECTION.get() != null;
    }

    /**
     * Chiude e pulisce la Connection dal thread corrente.
     */
    public static void close() {
        Connection conn = CONNECTION.get();
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                // Log o gestisci secondo la tua strategia
                System.err.println("Errore durante la chiusura della Connection: " + e.getMessage());
            } finally {
                CONNECTION.remove();
            }
        }
    }

    /**
     * Pulisce la Connection dal ThreadLocal senza chiuderla.
     * Utile se la Connection è gestita esternamente.
     */
    public static void clear() {
        CONNECTION.remove();
    }
}
