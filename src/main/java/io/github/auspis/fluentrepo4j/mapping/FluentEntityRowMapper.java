package io.github.auspis.fluentrepo4j.mapping;

import io.github.auspis.fluentrepo4j.FluentPersistable;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        domainType = entityInformation.getJavaType();
        columnToField = entityInformation.getColumnToFieldMap();
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
                        field.set(instance, convertIfNeeded(value, field.getType()));
                    } catch (IllegalAccessException e) {
                        throw new SQLException(
                                "Cannot set field " + field.getName() + " on " + domainType.getSimpleName(), e);
                    }
                }
            }
        }

        if (instance instanceof FluentPersistable<?> fp) {
            fp.markPersisted();
        }
        return instance;
    }

    /**
     * Converts a value to the target type if needed.
     * Handles numeric type coercion (e.g., Integer → Long) that occurs when
     * JDBC drivers return a different numeric type than the entity field expects.
     */
    private Object convertIfNeeded(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (value instanceof Number number) {
            if (targetType == Long.class || targetType == long.class) {
                return number.longValue();
            }
            if (targetType == Integer.class || targetType == int.class) {
                return number.intValue();
            }
            if (targetType == Double.class || targetType == double.class) {
                return number.doubleValue();
            }
            if (targetType == Float.class || targetType == float.class) {
                return number.floatValue();
            }
            if (targetType == Short.class || targetType == short.class) {
                return number.shortValue();
            }
            if (targetType == Byte.class || targetType == byte.class) {
                return number.byteValue();
            }
        }
        if (value instanceof java.sql.Timestamp timestamp && targetType == LocalDateTime.class) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date sqlDate && targetType == LocalDate.class) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof byte[] bytes && targetType == String.class) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return value;
    }
}
