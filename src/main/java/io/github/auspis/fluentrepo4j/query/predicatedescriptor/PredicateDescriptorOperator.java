package io.github.auspis.fluentrepo4j.query.predicatedescriptor;

/**
 * Specifies the comparison operator for a {@link PropertyPredicateDescriptor}.
 * This is a neutral representation, independent of Spring Data's
 * {@link org.springframework.data.repository.query.parser.Part.Type}.
 */
public enum PredicateDescriptorOperator {

    /** {@code property = ?} */
    EQUALS,

    /** {@code property != ?} (NOT Equals) */
    NOT_EQUALS,

    /** {@code property < ?} */
    LESS_THAN,

    /** {@code property <= ?} */
    LESS_THAN_EQUAL,

    /** {@code property > ?} */
    GREATER_THAN,

    /** {@code property >= ?} */
    GREATER_THAN_EQUAL,

    /** {@code property BETWEEN ? AND ?} (consumes 2 parameters) */
    BETWEEN,

    /** {@code property IS NULL} (no parameter) */
    IS_NULL,

    /** {@code property IS NOT NULL} (no parameter) */
    IS_NOT_NULL,

    /** {@code property LIKE ?} (caller supplies the full pattern including wildcards) */
    LIKE,

    /** {@code property NOT LIKE ?} */
    NOT_LIKE,

    /** {@code property LIKE ?%} */
    STARTING_WITH,

    /** {@code property LIKE %?} */
    ENDING_WITH,

    /** {@code property LIKE %?%} */
    CONTAINING,

    /** {@code property NOT LIKE %?%} */
    NOT_CONTAINING,

    /** {@code property IN (?...)} (parameter must be an {@link Iterable}) */
    IN,

    /** {@code property NOT IN (?...)} */
    NOT_IN,

    /** {@code property = TRUE} (no parameter) */
    TRUE,

    /** {@code property = FALSE} (no parameter) */
    FALSE
}
