package io.github.auspis.fluentrepo4j.test.fragment.mixed;

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

/** Aware fragment impl — receives Fluent context for DSL queries. */
public class MixedAwareQueriesImpl implements MixedAwareQueries, FluentRepositoryContextAware {

    private FluentRepositoryContext context;

    @Override
    public void setFluentRepositoryContext(FluentRepositoryContext context) {
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
                    User user = new User(rs.getString("name"), rs.getString("email"));
                    user.setId(rs.getLong("id"));
                    results.add(user);
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute findUsersByNameContaining", e);
        } finally {
            context.connectionProvider().releaseConnection(conn);
        }
    }

    /** Exposes the injected context for test assertions. */
    public FluentRepositoryContext getContext() {
        return context;
    }
}
