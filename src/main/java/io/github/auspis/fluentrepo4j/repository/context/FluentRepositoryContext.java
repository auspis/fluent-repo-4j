package io.github.auspis.fluentrepo4j.repository.context;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityWriter;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.util.Objects;

/**
 * Immutable context carrying the repository-specific infrastructure and entity-mapping
 * utilities resolved at bootstrap time.
 *
 * <p>In multi-datasource configurations each repository group (defined via
 * {@link io.github.auspis.fluentrepo4j.config.EnableFluentRepositories @EnableFluentRepositories})
 * receives its own context, bound to the {@code DataSource} and dialect configured for that group.
 * Custom fragment implementations that implement {@link FluentRepositoryContextAware} receive
 * this context automatically, ensuring they execute queries against the correct datasource
 * with the correct SQL dialect and have access to the entity's row mapper and writer.
 *
 * @param <T> the entity type managed by the owning repository
 */
public final class FluentRepositoryContext<T> {

    private final FluentRepositoryInfrastructure infrastructure;
    private final FluentEntityRowMapper<T> rowMapper;
    private final FluentEntityWriter<T> writer;

    FluentRepositoryContext(
            FluentRepositoryInfrastructure infrastructure,
            FluentEntityRowMapper<T> rowMapper,
            FluentEntityWriter<T> writer) {
        Objects.requireNonNull(infrastructure, "FluentRepositoryInfrastructure must not be null");
        Objects.requireNonNull(rowMapper, "FluentEntityRowMapper must not be null");
        Objects.requireNonNull(writer, "FluentEntityWriter must not be null");
        this.infrastructure = infrastructure;
        this.rowMapper = rowMapper;
        this.writer = writer;
    }

    /** Returns the dialect-specific DSL instance for this repository group. */
    public DSL dsl() {
        return infrastructure.dsl();
    }

    /** Returns the connection provider bound to this repository group's datasource. */
    public FluentConnectionProvider connectionProvider() {
        return infrastructure.connectionProvider();
    }

    /** Returns the type-safe row mapper for the entity managed by the owning repository. */
    public FluentEntityRowMapper<T> rowMapper() {
        return rowMapper;
    }

    /** Returns the type-safe writer for the entity managed by the owning repository. */
    public FluentEntityWriter<T> writer() {
        return writer;
    }

    /** Returns the infrastructure record — package-private, used by the injection guard. */
    FluentRepositoryInfrastructure infrastructure() {
        return infrastructure;
    }

    /**
     * Checks whether this context shares the same infrastructure (DSL and connection provider
     * by identity) as the given other context. Used by the injection guard to detect singleton
     * fragment overwrite across repository groups.
     *
     * @param other the context to compare against
     * @return {@code true} if both contexts reference the same infrastructure instance
     */
    public boolean hasSameInfrastructure(FluentRepositoryContext<?> other) {
        return this.infrastructure == other.infrastructure;
    }
}
