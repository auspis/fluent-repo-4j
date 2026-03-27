package io.github.auspis.fluentrepo4j.query.predicatedescriptor;

import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentsql4j.ast.core.predicate.Predicate;

/**
 * Sealed base for the intermediate criterion tree produced by
 * {@link io.github.auspis.fluentrepo4j.parse.PartTreeAdapter}.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link PropertyPredicateDescriptor} – a leaf predicate on a single property</li>
 *   <li>{@link CompositePredicateDescriptor} – an AND or OR of child criteria</li>
 *   <li>{@link NullPredicateDescriptor} – null-object representing absence of predicate</li>
 * </ul>
 */
public sealed interface PredicateDescriptor
        permits PropertyPredicateDescriptor, CompositePredicateDescriptor, NullPredicateDescriptor {

    /**
     * Convert this criterion into a fluent-sql-4j {@link Predicate} using the
     * provided metadata provider and runtime arguments.
     *
     * <p>Implementations may return {@link io.github.auspis.fluentsql4j.ast.core.predicate.NullPredicate}
     * to indicate the absence of a real predicate; callers may then add the
     * result to builders without extra null-checks.
     */
    Predicate toPredicate(PropertyMetadataProvider<?, ?> metadataProvider, Object[] args);
}
