package io.github.auspis.fluentrepo4j.query.predicatedescriptor;

/**
 * Sealed base for the intermediate criterion tree produced by
 * {@link io.github.auspis.fluentrepo4j.parse.PartTreeAdapter}.
 *
 * <p>Implementations are pure data carriers; translation to fluent-sql-4j
 * predicates is handled by
 * {@link io.github.auspis.fluentrepo4j.query.mapper.dsl.PredicateDescriptorMapper}.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link PropertyPredicateDescriptor} – a leaf predicate on a single property</li>
 *   <li>{@link CompositePredicateDescriptor} – an AND or OR of child criteria</li>
 *   <li>{@link NullPredicateDescriptor} – null-object representing absence of predicate</li>
 * </ul>
 */
public sealed interface PredicateDescriptor
        permits PropertyPredicateDescriptor, CompositePredicateDescriptor, NullPredicateDescriptor {}
