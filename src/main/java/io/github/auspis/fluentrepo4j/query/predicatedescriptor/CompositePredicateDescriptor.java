package io.github.auspis.fluentrepo4j.query.predicatedescriptor;

import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentsql4j.ast.core.predicate.AndOr;
import io.github.auspis.fluentsql4j.ast.core.predicate.NullPredicate;
import io.github.auspis.fluentsql4j.ast.core.predicate.Predicate;

import java.util.ArrayList;
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

    @Override
    public Predicate toPredicate(PropertyMetadataProvider<?, ?> metadataProvider, Object[] args) {
        if (children.isEmpty()) {
            return new NullPredicate();
        }

        List<Predicate> predicates = new ArrayList<>();
        for (PredicateDescriptor child : children) {
            Predicate p = child.toPredicate(metadataProvider, args);
            if (p == null || p instanceof NullPredicate) {
                continue;
            }
            predicates.add(p);
        }

        if (predicates.isEmpty()) {
            return new NullPredicate();
        }

        return type == CompositeType.AND ? AndOr.and(predicates) : AndOr.or(predicates);
    }
}
