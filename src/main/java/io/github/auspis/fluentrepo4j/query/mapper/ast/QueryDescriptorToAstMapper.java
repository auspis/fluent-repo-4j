package io.github.auspis.fluentrepo4j.query.mapper.ast;

import io.github.auspis.fluentrepo4j.query.QueryDescriptor;

/**
 * Scaffold for a future AST-based query mapper.
 *
 * <p><strong>This class is intentionally a placeholder.</strong>  It is not used
 * by the default execution pipeline; use
 * {@link io.github.auspis.fluentrepo4j.query.mapper.dsl.QueryDescriptorToDslMapper}
 * instead.
 *
 * <p>Future implementation should:
 * <ol>
 *   <li>Walk the {@link QueryDescriptor} criterion tree.</li>
 *   <li>Build a fluent-sql-4j AST ({@code SelectStatement}, {@code Where}, etc.)
 *       directly, without going through the DSL builder layer.</li>
 *   <li>Allow more advanced transformations such as query rewriting,
 *       optimisation passes, and multi-query plan generation.</li>
 * </ol>
 */
public final class QueryDescriptorToAstMapper {

    private QueryDescriptorToAstMapper() {}

    /**
     * Not yet implemented.
     *
     * @throws UnsupportedOperationException always
     */
    public static void map(QueryDescriptor descriptor, Object[] args) {
        throw new UnsupportedOperationException("QueryDescriptorToAstMapper is a scaffold and not yet implemented. "
                + "Use QueryDescriptorToDslMapper for production use.");
    }
}
