package io.github.auspis.fluentrepo4j.query.predicatedescriptor;

import java.util.List;

/**
 * A composite criterion that combines child criteria with AND or OR.
 *
 * @param type     {@link CompositeType#AND} or {@link CompositeType#OR}
 * @param children the sub-criteria (at least one element)
 */
public record CompositePredicateDescriptor(CompositeType type, List<PredicateDescriptor> children)
        implements PredicateDescriptor {

    /**
     * Logical combinator type.
     */
    public enum CompositeType {
        AND,
        OR
    }

    public CompositePredicateDescriptor {
        children = List.copyOf(children);
    }
}
