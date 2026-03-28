package io.github.auspis.fluentrepo4j.query;

import io.github.auspis.fluentsql4j.ast.dql.clause.Sorting;

/**
 * A single column/direction pair derived from an {@code OrderBy} clause in a
 * Spring Data method name (e.g., {@code findByNameOrderByAgeDesc}).
 *
 * @param columnName the database column name (after property-to-column mapping)
 * @param direction  {@link Sorting.SortOrder#ASC} or {@link Sorting.SortOrder#DESC}
 */
public record OrderByClause(String columnName, Sorting.SortOrder direction) {}
