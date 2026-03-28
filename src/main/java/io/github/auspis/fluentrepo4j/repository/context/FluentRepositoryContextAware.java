package io.github.auspis.fluentrepo4j.repository.context;

/**
 * Callback interface for custom repository fragment implementations that need access
 * to the repository-specific {@link FluentRepositoryContext}.
 *
 * <p>When a fragment implementation class (the {@code ...Impl} class discovered by Spring Data)
 * implements this interface, the framework calls {@link #setFluentRepositoryContext} after
 * the bean is created but before the repository proxy is returned to the application.
 * The method is called exactly once per repository group bootstrap.
 *
 * <p>This is an <strong>opt-in</strong> contract: fragment implementations that do not implement
 * this interface continue to work normally as plain Spring beans with no context injection.
 *
 * <h3>Multi-datasource safety</h3>
 * <p>The injected context is always bound to the same {@code DataSource} and {@code DSL}
 * resolved for the owning repository group via
 * {@link io.github.auspis.fluentrepo4j.config.EnableFluentRepositories @EnableFluentRepositories}.
 * This guarantees that custom queries execute against the correct datasource with the correct
 * SQL dialect, even in configurations with multiple datasources.
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * public interface UserRepositoryCustomQueries {
 *     List<User> findActiveUsersByCity(String city);
 * }
 *
 * public class UserRepositoryCustomQueriesImpl
 *         implements UserRepositoryCustomQueries, FluentRepositoryContextAware {
 *
 *     private FluentRepositoryContext context;
 *
 *     @Override
 *     public void setFluentRepositoryContext(FluentRepositoryContext context) {
 *         this.context = context;
 *     }
 *
 *     @Override
 *     public List<User> findActiveUsersByCity(String city) {
 *         DSL dsl = context.dsl();
 *         Connection conn = context.connectionProvider().getConnection();
 *         try {
 *             PreparedStatement ps = dsl.selectAll().from("users")
 *                     .where().column("active").eq(true)
 *                     .and().column("address").eq(city)
 *                     .build(conn);
 *             // execute and map results...
 *         } finally {
 *             context.connectionProvider().releaseConnection(conn);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Important:</strong> do not use the context inside the fragment constructor.
 * The context is injected after construction, during repository proxy creation.
 */
public interface FluentRepositoryContextAware {

    /**
     * Sets the repository-specific context for this fragment implementation.
     *
     * @param context the fluent repository context; never {@code null}
     */
    void setFluentRepositoryContext(FluentRepositoryContext context);
}
