package io.github.auspis.fluentrepo4j.meta;

import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.mapping.NamingUtils;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Adapts {@link FluentEntityInformation} to provide property-path-to-column-name
 * resolution for dynamic query building.
 *
 * <p>Only flat (single-level) property paths are supported.  Dotted paths such
 * as {@code "address.city"} are not supported in this release; an
 * {@link IllegalArgumentException} is thrown for those.
 *
 * @param <T>  entity type
 * @param <ID> identifier type
 */
public final class PropertyMetadataProvider<T, ID> {

    private final FluentEntityInformation<T, ID> entityInformation;

    public PropertyMetadataProvider(FluentEntityInformation<T, ID> entityInformation) {
        this.entityInformation = entityInformation;
    }

    /**
     * Returns the database column name for the given entity property path.
     *
     * @param propertyPath simple property name (e.g. {@code "name"}, {@code "email"})
     * @return corresponding column name
     * @throws IllegalArgumentException if the property cannot be resolved
     */
    public String resolveColumn(String propertyPath) {
        if (propertyPath.contains(".")) {
            throw new IllegalArgumentException("Nested property path '" + propertyPath
                    + "' is not supported in this release. " + "Only flat, single-level properties are supported.");
        }

        // Look up via fieldToColumn map using the field name
        Map<Field, String> fieldToColumn = entityInformation.getFieldToColumnMap();
        for (Map.Entry<Field, String> entry : fieldToColumn.entrySet()) {
            if (entry.getKey().getName().equals(propertyPath)) {
                return entry.getValue();
            }
        }

        // Fall back to snake_case conversion in case the property is not directly mapped
        // but column exists in the entity's column map
        String snakeCased = NamingUtils.toSnakeCase(propertyPath);
        if (entityInformation.getColumnToFieldMap().containsKey(snakeCased)) {
            return snakeCased;
        }

        throw new IllegalArgumentException("Property '" + propertyPath + "' not found in entity "
                + entityInformation.getJavaType().getSimpleName()
                + ". Available properties: "
                + entityInformation.getFieldToColumnMap().keySet().stream()
                        .map(Field::getName)
                        .toList());
    }

    /**
     * Returns the table name for the entity.
     */
    public String getTableName() {
        return entityInformation.getTableName();
    }
}
