package io.github.auspis.fluentrepo4j.query.predicatedescriptor;

import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.CompositePredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.NullPredicateDescriptor;
import io.github.auspis.fluentrepo4j.query.predicatedescriptor.PredicateDescriptor.PropertyPredicateDescriptor;

import java.util.List;

/**
 * Sealed base for the intermediate criterion tree produced by
 * {@link io.github.auspis.fluentrepo4j.parse.PartTreeAdapter}.
 *
 * <p>Implementations are pure data carriers; translation to fluent-sql-4j
 * predicates is handled by
 * {@link io.github.auspis.fluentrepo4j.query.mapper.dsl.PredicateDescriptorMapper}.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link PropertyPredicateDescriptor} – a leaf predicate on a single property</li>
 *   <li>{@link CompositePredicateDescriptor} – an AND or OR of child criteria</li>
 *   <li>{@link NullPredicateDescriptor} – null-object representing absence of predicate</li>
 * </ul>
 */
public sealed interface PredicateDescriptor
        permits PropertyPredicateDescriptor, CompositePredicateDescriptor, NullPredicateDescriptor {

    public enum NullPredicateDescriptor implements PredicateDescriptor {
        INSTANCE
    }

    public record PropertyPredicateDescriptor(
            String name, PredicateDescriptorOperator operator, boolean ignoreCase, int paramIndex, int paramCount)
            implements PredicateDescriptor {}

    public record CompositePredicateDescriptor(CompositeType type, List<PredicateDescriptor> children)
            implements PredicateDescriptor {
        public enum CompositeType {
            AND,
            OR
        }

        public CompositePredicateDescriptor {
            children = List.copyOf(children);
        }
    }
}
