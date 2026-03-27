package io.github.auspis.fluentrepo4j.query.predicatedescriptor;

import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentsql4j.ast.core.predicate.NullPredicate;
import io.github.auspis.fluentsql4j.ast.core.predicate.Predicate;

/**
 * Null-object sentinel for "no predicate". Use {@link #INSTANCE} where
 * previously {@code null} was used to indicate absence of a predicate.
 */
public enum NullPredicateDescriptor implements PredicateDescriptor {
    INSTANCE;

    @Override
    public Predicate toPredicate(PropertyMetadataProvider<?, ?> metadataProvider, Object[] args) {
        return new NullPredicate();
    }
}
