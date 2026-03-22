package io.github.auspis.fluentrepo4j.query;

import io.github.auspis.fluentrepo4j.query.criterion.Criterion;

import java.util.List;

/**
 * Neutral intermediate representation of a Spring Data-style repository query
 * derived from a method name (e.g. {@code findByEmailAndNameOrderByAgeDesc}).
 *
 * <p>This descriptor is built once per repository method by
 * {@link io.github.auspis.fluentrepo4j.parse.PartTreeAdapter} and then cached.
 * At execution time it is consumed by
 * {@link io.github.auspis.fluentrepo4j.query.mapper.dsl.QueryDescriptorToDslMapper}
 * to build a fluent-sql-4j {@code SelectBuilder} or {@code DeleteBuilder}.
 *
 * @param operation         the query operation (FIND / COUNT / EXISTS / DELETE)
 * @param distinct          whether {@code SELECT DISTINCT} should be emitted
 * @param maxResults        maximum number of rows to return; {@code null} if
 *                          unrestricted (used for {@code findTop3By…})
 * @param root              root of the criterion tree; {@code null} if there is
 *                          no WHERE predicate (e.g. {@code findAll})
 * @param orderBy           static order-by clauses derived from the method name;
 *                          may be empty
 * @param pageableParamIndex zero-based index of the {@code Pageable} parameter
 *                          in the method signature, or {@code -1} if absent
 * @param sortParamIndex    zero-based index of the {@code Sort} parameter in the
 *                          method signature, or {@code -1} if absent
 */
public record QueryDescriptor(
        QueryOperation operation,
        boolean distinct,
        Integer maxResults,
        Criterion root,
        List<OrderByClause> orderBy,
        int pageableParamIndex,
        int sortParamIndex) {

    public QueryDescriptor {
        orderBy = List.copyOf(orderBy);
    }
}
