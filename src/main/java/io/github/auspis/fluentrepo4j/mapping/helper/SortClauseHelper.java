package io.github.auspis.fluentrepo4j.mapping.helper;

import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Sort;

/**
 * Resolves {@link Sort} properties (Java field names) into database column names
 * using entity metadata from {@link FluentEntityInformation}.
 */
public class SortClauseHelper {

    private final Map<String, String> fieldNameToColumn;

    public record ColumnOrder(String columnName, Sort.Direction direction) {}

    public SortClauseHelper(FluentEntityInformation<?, ?> entityInformation) {
        this.fieldNameToColumn = Collections.unmodifiableMap(entityInformation.getFieldToColumnMap().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().getName(), Map.Entry::getValue)));
    }

    /**
     * Resolves the given {@link Sort} into an ordered list of {@link ColumnOrder} entries.
     *
     * @param sort the sort specification using Java field names
     * @return resolved column orders, empty list if sort is unsorted
     * @throws InvalidDataAccessApiUsageException if a sort property does not match any entity field
     */
    public List<ColumnOrder> resolve(Sort sort) {
        if (sort.isUnsorted()) {
            return List.of();
        }

        List<ColumnOrder> result = new ArrayList<>();
        for (Sort.Order order : sort) {
            String fieldName = order.getProperty();
            String columnName = fieldNameToColumn.get(fieldName);
            if (columnName == null) {
                throw new InvalidDataAccessApiUsageException("No property '" + fieldName + "' found on entity. "
                        + "Available properties: " + fieldNameToColumn.keySet());
            }
            result.add(new ColumnOrder(columnName, order.getDirection()));
        }
        return result;
    }
}
