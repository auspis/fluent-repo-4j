package io.github.auspis.fluentrepo4j.query.predicatedescriptor;

/**
 * Null-object sentinel for "no predicate". Use {@link #INSTANCE} where
 * previously {@code null} was used to indicate absence of a predicate.
 */
public enum NullPredicateDescriptor implements PredicateDescriptor {
    INSTANCE
}
