package io.github.auspis.repo4j.mapping;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts column values from an entity instance for INSERT/UPDATE operations.
 * Uses the same annotation-based metadata as {@link FluentEntityRowMapper}.
 *
 * @param <T> the entity type
 */
public class FluentEntityWriter<T> {

    private final FluentEntityInformation<T, ?> entityInformation;

    public FluentEntityWriter(FluentEntityInformation<T, ?> entityInformation) {
        this.entityInformation = entityInformation;
    }

    /**
     * Returns all column values (including ID) from the given entity.
     *
     * @return map of column name → value
     */
    public Map<String, Object> getAllColumnValues(T entity) {
        return extractValues(entity, entityInformation.getColumnToFieldMap());
    }

    /**
     * Returns column values excluding the ID column (for UPDATE SET clause).
     *
     * @return map of column name → value (no ID)
     */
    public Map<String, Object> getNonIdColumnValues(T entity) {
        return extractValues(entity, entityInformation.getNonIdColumnToFieldMap());
    }

    private Map<String, Object> extractValues(T entity, Map<String, Field> columnFieldMap) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Field> entry : columnFieldMap.entrySet()) {
            try {
                values.put(entry.getKey(), entry.getValue().get(entity));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Cannot read field " + entry.getValue().getName()
                                + " on " + entity.getClass().getSimpleName(), e);
            }
        }
        return values;
    }
}
