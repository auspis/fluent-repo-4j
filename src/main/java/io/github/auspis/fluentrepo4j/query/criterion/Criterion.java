package io.github.auspis.fluentrepo4j.query.criterion;

/**
 * Sealed base for the intermediate criterion tree produced by
 * {@link io.github.auspis.fluentrepo4j.parse.PartTreeAdapter}.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link PropertyCriterion} – a leaf predicate on a single property</li>
 *   <li>{@link CompositeCriterion} – an AND or OR of child criteria</li>
 * </ul>
 */
public sealed interface Criterion permits PropertyCriterion, CompositeCriterion {}
