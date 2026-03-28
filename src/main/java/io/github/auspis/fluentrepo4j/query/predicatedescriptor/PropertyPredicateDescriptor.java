package io.github.auspis.fluentrepo4j.query.predicatedescriptor;

/**
 * A leaf criterion that compares a single entity property against one or more
 * method arguments.
 */
public record PropertyPredicateDescriptor(
        String name, PredicateDescriptorOperator operator, boolean ignoreCase, int paramIndex, int paramCount)
        implements PredicateDescriptor {}
