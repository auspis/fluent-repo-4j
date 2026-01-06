package io.github.auspis.repo4j.core;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Functional interface to map a ResultSet row to a domain object.
 *
 * @param <T> the type of the domain object
 */
@FunctionalInterface
public interface RowMapper<T> {
    /**
     * Maps a ResultSet row to an object of type T.
     *
     * @param rs the ResultSet positioned on the row to map
     * @return the mapped object
     * @throws SQLException if an error occurs during data extraction
     */
    T mapRow(ResultSet rs) throws SQLException;
}
