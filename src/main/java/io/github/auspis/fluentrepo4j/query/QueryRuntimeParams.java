package io.github.auspis.fluentrepo4j.query;

import java.util.List;

/**
 * Neutral runtime parameters for query building, decoupled from Spring Data types.
 *
 * @param runtimeSort ORDER BY clauses derived from a runtime {@code Sort} or {@code Pageable}
 * @param pageWindow  pagination window, or {@code null} if not paginated
 */
public record QueryRuntimeParams(List<OrderByClause> runtimeSort, PageWindow pageWindow) {

    public QueryRuntimeParams {
        runtimeSort = List.copyOf(runtimeSort);
    }

    public static QueryRuntimeParams empty() {
        return new QueryRuntimeParams(List.of(), null);
    }
}
