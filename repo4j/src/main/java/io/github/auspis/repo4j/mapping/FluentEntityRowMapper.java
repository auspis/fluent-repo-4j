package io.github.auspis.repo4j.mapping;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@link RowMapper} implementation that maps a {@link ResultSet} row to an entity
 * instance using the metadata from {@link FluentEntityInformation}.
 * <p>
 * Maps columns to fields by matching column names (case-insensitive) against
 * the resolved column mappings (from {@code @Column} annotations or naming convention).
 * </p>
 *
 * @param <T> the entity type
 */
public class FluentEntityRowMapper<T> implements RowMapper<T> {

    private final Class<T> domainType;
    private final Map<String, Field> columnToField;

    public FluentEntityRowMapper(FluentEntityInformation<T, ?> entityInformation) {
        this.domainType = entityInformation.getJavaType();
        this.columnToField = entityInformation.getColumnToFieldMap();
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        T instance = BeanUtils.instantiateClass(domainType);
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String columnLabel = metaData.getColumnLabel(i).toLowerCase();
            Field field = columnToField.get(columnLabel);

            if (field == null) {
                // Try with the column name (without alias)
                columnLabel = metaData.getColumnName(i).toLowerCase();
                field = columnToField.get(columnLabel);
            }

            if (field != null) {
                Object value = rs.getObject(i);
                if (value != null || !field.getType().isPrimitive()) {
                    try {
                        field.set(instance, value);
                    } catch (IllegalAccessException e) {
                        throw new SQLException("Cannot set field " + field.getName()
                                + " on " + domainType.getSimpleName(), e);
                    }
                }
            }
        }

        return instance;
    }
}
