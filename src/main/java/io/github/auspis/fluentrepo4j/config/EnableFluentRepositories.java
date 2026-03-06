package io.github.auspis.fluentrepo4j.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Import;
import org.springframework.data.repository.query.QueryLookupStrategy;

import io.github.auspis.fluentrepo4j.repository.FluentRepositoryFactoryBean;

/**
 * Annotation to enable Fluent SQL repository support.
 * Scans for interfaces extending Spring Data {@code Repository} and creates
 * proxy beans backed by {@link io.github.auspis.fluentrepo4j.repository.SimpleFluentRepository}.
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
}
