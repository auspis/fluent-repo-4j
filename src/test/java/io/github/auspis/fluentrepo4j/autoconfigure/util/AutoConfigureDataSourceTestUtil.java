package io.github.auspis.fluentrepo4j.autoconfigure.util;

import io.github.auspis.fluentsql4j.test.util.database.TestDatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

public final class AutoConfigureDataSourceTestUtil {

    private AutoConfigureDataSourceTestUtil() {}

    public static void resetUsersTable(DataSource dataSource, long id, String name, String email) {
        try (Connection connection = dataSource.getConnection()) {
            TestDatabaseUtil.H2.dropUsersTable(connection);
            TestDatabaseUtil.H2.createUsersTable(connection);

            try (PreparedStatement statement =
                    connection.prepareStatement("INSERT INTO users (id, name, email) VALUES (?, ?, ?)")) {
                statement.setLong(1, id);
                statement.setString(2, name);
                statement.setString(3, email);
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to reset users table for auto-configuration test", exception);
        }
    }
}
