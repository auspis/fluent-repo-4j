package io.github.auspis.fluentrepo4j.query.criterion;

import java.util.List;

/**
 * A composite criterion that combines child criteria with AND or OR.
 *
 * @param type     {@link CompositeType#AND} or {@link CompositeType#OR}
 * @param children the sub-criteria (at least one element)
 */
public record CompositeCriterion(CompositeType type, List<Criterion> children) implements Criterion {

    /**
     * Logical combinator type.
     */
    public enum CompositeType {
        AND,
        OR
    }

    public CompositeCriterion {
        children = List.copyOf(children);
    }
}
