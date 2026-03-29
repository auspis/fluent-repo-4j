package io.github.auspis.fluentrepo4j.test.fragment;

import io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContext;
import io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContextAware;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom fragment implementation that uses {@link FluentRepositoryContextAware}
 * to receive the repository-specific DSL, connection provider, and entity row mapper.
 */
public class UserCustomQueriesImpl implements UserCustomQueries, FluentRepositoryContextAware<User> {

    private FluentRepositoryContext<User> context;

    @Override
    public FluentRepositoryContext<User> getFluentRepositoryContext() {
        return context;
    }

    @Override
    public void setFluentRepositoryContext(FluentRepositoryContext<User> context) {
        this.context = context;
    }

    @Override
    public List<User> findUsersByNameContaining(String namePart) {
        DSL dsl = context.dsl();
        Connection conn = context.connectionProvider().getConnection();
        try {
            PreparedStatement ps = dsl.selectAll()
                    .from("users")
                    .where()
                    .column("name")
                    .like("%" + namePart + "%")
                    .build(conn);
            try (ps;
                    ResultSet rs = ps.executeQuery()) {
                List<User> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(context.rowMapper().mapRow(rs, rs.getRow()));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute findUsersByNameContaining", e);
        } finally {
            context.connectionProvider().releaseConnection(conn);
        }
    }

    @Override
    public long countActiveUsers() {
        DSL dsl = context.dsl();
        Connection conn = context.connectionProvider().getConnection();
        try {
            PreparedStatement ps = dsl.select()
                    .countStar()
                    .from("users")
                    .where()
                    .column("active")
                    .eq(true)
                    .build(conn);
            try (ps;
                    ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute countActiveUsers", e);
        } finally {
            context.connectionProvider().releaseConnection(conn);
        }
    }

    /** Exposes the injected context for test assertions. */
    public FluentRepositoryContext<User> getContext() {
        return context;
    }
}
