package io.github.auspis.fluentrepo4j.query;

/**
 * Represents the type of operation inferred from a repository method name.
 * Derived from Spring Data's {@link org.springframework.data.repository.query.parser.PartTree}
 * subject segment (find…, count…, exists…, delete…).
 */
public enum QueryOperation {

    /** SELECT query returning entities or projections. */
    FIND,

    /** SELECT COUNT(*) query. */
    COUNT,

    /** SELECT COUNT(*) > 0 query returning boolean. */
    EXISTS,

    /** DELETE query returning void or number of deleted rows. */
    DELETE
}
