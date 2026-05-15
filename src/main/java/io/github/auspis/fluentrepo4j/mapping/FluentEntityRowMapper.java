package io.github.auspis.fluentrepo4j.mapping;

import io.github.auspis.fluentrepo4j.FluentPersistable;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
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

    private record ConversionStrategy(BiPredicate<Object, Class<?>> predicate, Function<Object, Object> converter) {}

    private static final List<ConversionStrategy> CONVERSION_STRATEGIES = List.of(
            new ConversionStrategy(
                    (v, tt) -> v instanceof Number && (tt == Long.class || tt == long.class),
                    v -> ((Number) v).longValue()),
            new ConversionStrategy(
                    (v, tt) -> v instanceof Number && (tt == Integer.class || tt == int.class),
                    v -> ((Number) v).intValue()),
            new ConversionStrategy(
                    (v, tt) -> v instanceof Number && (tt == Double.class || tt == double.class),
                    v -> ((Number) v).doubleValue()),
            new ConversionStrategy(
                    (v, tt) -> v instanceof Number && (tt == Float.class || tt == float.class),
                    v -> ((Number) v).floatValue()),
            new ConversionStrategy(
                    (v, tt) -> v instanceof Number && (tt == Short.class || tt == short.class),
                    v -> ((Number) v).shortValue()),
            new ConversionStrategy(
                    (v, tt) -> v instanceof Number && (tt == Byte.class || tt == byte.class),
                    v -> ((Number) v).byteValue()),
            new ConversionStrategy(
                    (v, tt) -> v instanceof java.sql.Timestamp && tt == LocalDateTime.class,
                    v -> ((java.sql.Timestamp) v).toLocalDateTime()),
            new ConversionStrategy(
                    (v, tt) -> v instanceof java.sql.Date && tt == LocalDate.class,
                    v -> ((java.sql.Date) v).toLocalDate()),
            new ConversionStrategy(
                    (v, tt) -> v instanceof byte[] && tt == String.class,
                    v -> new String((byte[]) v, java.nio.charset.StandardCharsets.UTF_8)));

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
        if (value == null || targetType.isInstance(value)) return value;

        for (ConversionStrategy entry : CONVERSION_STRATEGIES) {
            if (entry.predicate().test(value, targetType)) {
                return entry.converter().apply(value);
            }
        }
        return value;
    }
}
