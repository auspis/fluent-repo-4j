package io.github.auspis.fluentrepo4j.query.criterion;

import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentsql4j.ast.core.predicate.NullPredicate;
import io.github.auspis.fluentsql4j.ast.core.predicate.Predicate;

/**
 * Null-object sentinel for "no predicate". Use {@link #INSTANCE} where
 * previously {@code null} was used to indicate absence of a predicate.
 */
public enum NullCriterion implements Criterion {
    INSTANCE;

    @Override
    public Predicate toPredicate(PropertyMetadataProvider<?, ?> metadataProvider, Object[] args) {
        return new NullPredicate();
    }
}
