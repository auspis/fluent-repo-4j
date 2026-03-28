package io.github.auspis.fluentrepo4j.test.fragment;

import io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContext;
import io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContextAware;
import io.github.auspis.fluentrepo4j.test.domain.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom fragment implementation for the primary datasource.
 */
public class PrimaryCustomQueriesImpl implements PrimaryCustomQueries, FluentRepositoryContextAware {

    private FluentRepositoryContext context;

    @Override
    public void setFluentRepositoryContext(FluentRepositoryContext context) {
        this.context = context;
    }

    @Override
    public List<User> findUsersByNamePrefix(String prefix) {
        Connection conn = context.connectionProvider().getConnection();
        try {
            PreparedStatement ps = context.dsl()
                    .selectAll()
                    .from("users")
                    .where()
                    .column("name")
                    .like(prefix + "%")
                    .build(conn);
            try (ps;
                    ResultSet rs = ps.executeQuery()) {
                List<User> results = new ArrayList<>();
                while (rs.next()) {
                    User user = new User(rs.getString("name"), rs.getString("email"));
                    user.setId(rs.getLong("id"));
                    results.add(user);
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute findUsersByNamePrefix", e);
        } finally {
            context.connectionProvider().releaseConnection(conn);
        }
    }

    public FluentRepositoryContext getContext() {
        return context;
    }
}
