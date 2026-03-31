package io.github.auspis.fluentrepo4j.repository.context;

/**
 * Callback interface for custom repository fragment implementations that need access
 * to the repository-specific {@link FluentRepositoryContext}.
 *
 * <p>When a fragment implementation class (the {@code ...Impl} class discovered by Spring Data)
 * implements this interface, the framework calls {@link #setFluentRepositoryContext} after
 * the bean is created but before the repository proxy is returned to the application.
 * The method is called once for each repository proxy instance during its creation and may be
 * invoked multiple times within a single application context.
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
 * <h3>Singleton overwrite protection</h3>
 * <p>If a fragment bean is shared across multiple repository groups that reference different
 * datasources, the framework detects the conflict at bootstrap time and throws an
 * {@link IllegalStateException}. This prevents silent datasource mismatch bugs.
 * To resolve, create a separate fragment implementation per repository group.
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * public interface UserRepositoryCustomQueries {
 *     List<User> findActiveUsersByCity(String city);
 * }
 *
 * public class UserRepositoryCustomQueriesImpl
 *         implements UserRepositoryCustomQueries, FluentRepositoryContextAware<User> {
 *
 *     private FluentRepositoryContext<User> context;
 *
 *     @Override
 *     public FluentRepositoryContext<User> getFluentRepositoryContext() {
 *         return context;
 *     }
 *
 *     @Override
 *     public void setFluentRepositoryContext(FluentRepositoryContext<User> context) {
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
 *             try (ps; ResultSet rs = ps.executeQuery()) {
 *                 List<User> results = new ArrayList<>();
 *                 while (rs.next()) {
 *                     results.add(context.rowMapper().mapRow(rs, rs.getRow()));
 *                 }
 *                 return results;
 *             }
 *         } finally {
 *             context.connectionProvider().releaseConnection(conn);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Important:</strong> do not use the context inside the fragment constructor.
 * The context is injected after construction, during repository proxy creation.
 *
 * @param <T> the entity type managed by the owning repository
 */
public interface FluentRepositoryContextAware<T> {

    /**
     * Returns the previously injected repository context, or {@code null} if
     * no context has been injected yet.
     *
     * @return the fluent repository context, or {@code null}
     */
    FluentRepositoryContext<T> getFluentRepositoryContext();

    /**
     * Sets the repository-specific context for this fragment implementation.
     *
     * @param context the fluent repository context; never {@code null}
     */
    void setFluentRepositoryContext(FluentRepositoryContext<T> context);
}
