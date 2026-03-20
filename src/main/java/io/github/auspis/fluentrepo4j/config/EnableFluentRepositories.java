package io.github.auspis.fluentrepo4j.config;

import io.github.auspis.fluentrepo4j.repository.FluentRepositoryFactoryBean;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Import;
import org.springframework.data.repository.query.QueryLookupStrategy;

/**
 * Annotation to enable Fluent SQL repository support.
 * Scans for interfaces extending Spring Data {@code Repository} and creates
 * proxy beans backed by {@link io.github.auspis.fluentrepo4j.repository.FluentRepository}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(FluentRepositoriesRegistrar.class)
public @interface EnableFluentRepositories {

    /**
     * Alias for {@link #basePackages()}.
     */
    String[] value() default {};

    /**
     * Base packages to scan for annotated repositories.
     */
    String[] basePackages() default {};

    /**
     * Type-safe alternative to {@link #basePackages()} for specifying the packages to
     * scan for annotated repositories.
     */
    Class<?>[] basePackageClasses() default {};

    /**
     * Specifies which types are eligible for component scanning.
     */
    Filter[] includeFilters() default {};

    /**
     * Specifies which types are not eligible for component scanning.
     */
    Filter[] excludeFilters() default {};

    /**
     * Returns the {@link org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport}
     * class to be used for each repository instance.
     */
    Class<?> repositoryFactoryBeanClass() default FluentRepositoryFactoryBean.class;

    /**
     * Returns the key of the {@link QueryLookupStrategy} to be used for lookup queries
     * for query methods.
     */
    QueryLookupStrategy.Key queryLookupStrategy() default QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND;

    /**
     * Configures the name of the {@link org.springframework.transaction.PlatformTransactionManager}
     * bean to be used with the repositories detected.
     */
    String transactionManagerRef() default "transactionManager";

    /**
     * Configures the name of the {@link javax.sql.DataSource} bean to be used for the
     * repositories detected by this annotation.
     * <p>
     * When set, fluent-repo-4j derives a {@link io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider}
     * and a dialect-specific {@link io.github.auspis.fluentsql4j.dsl.DSL} for this repository group.
     * Use this for the common multi-datasource scenario where each repository group should bind
     * to a dedicated {@code DataSource}.
     * </p>
     */
    String dataSourceRef() default "";

    /**
     * Configures the name of the {@link io.github.auspis.fluentsql4j.dsl.DSLRegistry} bean to use
     * when {@link #dataSourceRef()} is set and fluent-repo-4j needs to auto-detect the SQL dialect.
     */
    String dslRegistryRef() default "";

    /**
     * Configures the name of the {@link io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider}
     * bean to be used directly for the repositories detected by this annotation.
     * <p>
     * This takes precedence over {@link #dataSourceRef()} and is intended for advanced scenarios
     * where the application wants to provide a fully customized connection provider.
     * </p>
     */
    String connectionProviderRef() default "";

    /**
     * Configures the name of the {@link io.github.auspis.fluentsql4j.dsl.DSL} bean to be used
     * directly for the repositories detected by this annotation.
     * <p>
     * This takes precedence over {@link #dataSourceRef()} and is intended for advanced scenarios
     * where the application wants to provide a fully customized DSL instance.
     * </p>
     */
    String dslRef() default "";

    /**
     * Configures the location of where to find Spring Data named queries.
     * Will default to {@code classpath*:META-INF/jpa-named-queries.properties}.
     */
    String namedQueriesLocation() default "";

    /**
     * Configures whether nested repository-interfaces (e.g. a repository inside a service class)
     * should be discovered.
     */
    boolean considerNestedRepositories() default false;

    /**
     * Configures the postfix to be used when looking for custom repository implementations.
     * Defaults to {@code Impl}.
     */
    String repositoryImplementationPostfix() default "Impl";
}
