package io.github.auspis.fluentrepo4j.query;

import org.springframework.data.domain.Sort;

/**
 * A single column/direction pair derived from an {@code OrderBy} clause in a
 * Spring Data method name (e.g., {@code findByNameOrderByAgeDesc}).
 *
 * @param columnName the database column name (after property-to-column mapping)
 * @param direction  {@link Sort.Direction#ASC} or {@link Sort.Direction#DESC}
 */
public record OrderByClause(String columnName, Sort.Direction direction) {}
