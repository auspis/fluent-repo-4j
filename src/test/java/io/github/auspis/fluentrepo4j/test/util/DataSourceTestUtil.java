package io.github.auspis.fluentrepo4j.test.util;

import io.github.auspis.fluentsql4j.test.util.database.DataUtil.UserRecord;
import io.github.auspis.fluentsql4j.test.util.database.TestDatabaseUtil;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

public final class DataSourceTestUtil {

    private DataSourceTestUtil() {}

    public static void createUsersTable(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            TestDatabaseUtil.H2.dropUsersTable(connection);
            TestDatabaseUtil.H2.createUsersTable(connection);
        }
    }

    public static void insertUser(DataSource dataSource, long id, String name, String email) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            TestDatabaseUtil.H2.insertUser(
                    connection, new UserRecord(id, name, email, 30, true, "1995-01-01", "2023-01-01", "{}", "{}"));
        }
    }
}
