package io.github.auspis.fluentrepo4j.test.fragment.postfix;

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
 * Implementation with "Custom" postfix instead of the default "Impl".
 * Registered when {@code repositoryImplementationPostfix = "Custom"} is configured.
 */
public class PostfixQueriesCustom implements PostfixQueries, FluentRepositoryContextAware<User> {

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
    public List<User> findUsersByEmail(String email) {
        DSL dsl = context.dsl();
        Connection conn = context.connectionProvider().getConnection();
        try {
            PreparedStatement ps = dsl.selectAll()
                    .from("users")
                    .where()
                    .column("email")
                    .eq(email)
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
            throw new RuntimeException("Failed to execute findUsersByEmail", e);
        } finally {
            context.connectionProvider().releaseConnection(conn);
        }
    }

    /** Exposes the injected context for test assertions. */
    public FluentRepositoryContext<User> getContext() {
        return context;
    }
}
