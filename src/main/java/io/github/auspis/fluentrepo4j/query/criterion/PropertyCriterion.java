package io.github.auspis.fluentrepo4j.query.criterion;

/**
 * A leaf criterion that compares a single entity property against one or more
 * method arguments.
 *
 * @param propertyPath the property name as declared in the entity class (e.g.
 *                     {@code "name"}, {@code "age"})
 * @param operator     the comparison operator to apply
 * @param ignoreCase   {@code true} when the comparison must be case-insensitive
 *                     (maps to {@code LOWER(col) = LOWER(?)})
 * @param negated      {@code true} when the predicate is wrapped in a NOT
 * @param paramIndex   zero-based index of the first method argument consumed by
 *                     this criterion
 * @param paramCount   number of method arguments consumed (0 for IS_NULL /
 *                     IS_NOT_NULL / TRUE / FALSE; 2 for BETWEEN; 1 otherwise)
 */
public record PropertyCriterion(
        String propertyPath,
        CriterionOperator operator,
        boolean ignoreCase,
        boolean negated,
        int paramIndex,
        int paramCount)
        implements Criterion {}
