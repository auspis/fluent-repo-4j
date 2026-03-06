package io.github.auspis.repo4j.mapping;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.github.auspis.fluentsql4j.dsl.clause.SupportsWhere;
import io.github.auspis.fluentsql4j.dsl.clause.WhereConditionBuilder;
import io.github.auspis.fluentsql4j.dsl.insert.InsertBuilder;
import io.github.auspis.fluentsql4j.dsl.update.UpdateBuilder;

/**
 * Bridges the gap between Java's generic {@code Object} values and
 * fluent-sql-4j's typed method overloads ({@code String}, {@code Number},
 * {@code Boolean}, {@code LocalDate}, {@code LocalDateTime}).
 * <p>
 * fluent-sql-4j does not provide {@code eq(Object)} or {@code set(String, Object)}.
 * This helper dispatches at runtime to the correct typed overload.
 * </p>
 */
public final class DslTypeDispatcher {

    private DslTypeDispatcher() {
    }

    /**
     * Dispatches {@code column.eq(value)} to the correct typed overload.
     *
     * @param column the column comparator builder
     * @param value  the value to compare (must be String, Number, Boolean, LocalDate, or LocalDateTime)
     * @param <T>    the builder return type
     * @return the builder after applying the eq condition
     */
    @SuppressWarnings("unchecked")
    public static <T extends SupportsWhere<T>> T eq(WhereConditionBuilder<T> column, Object value) {
        if (value instanceof String s) return column.eq(s);
        if (value instanceof Number n) return column.eq(n);
        if (value instanceof Boolean b) return column.eq(b);
        if (value instanceof LocalDate d) return column.eq(d);
        if (value instanceof LocalDateTime dt) return column.eq(dt);
        throw new IllegalArgumentException(
                "Unsupported value type for eq(): " + (value == null ? "null" : value.getClass().getName())
                        + ". Supported types: String, Number, Boolean, LocalDate, LocalDateTime.");
    }

    /**
     * Dispatches {@code insertBuilder.set(column, value)} to the correct typed overload.
     * Null values are skipped (the column will not be included in the INSERT).
     *
     * @param builder    the insert builder
     * @param columnName the column name
     * @param value      the value to set
     * @return the builder after applying set (or unchanged if value is null)
     */
    public static InsertBuilder set(InsertBuilder builder, String columnName, Object value) {
        if (value == null) return builder;
        if (value instanceof String s) return builder.set(columnName, s);
        if (value instanceof Number n) return builder.set(columnName, n);
        if (value instanceof Boolean b) return builder.set(columnName, b);
        if (value instanceof LocalDate d) return builder.set(columnName, d);
        if (value instanceof LocalDateTime dt) return builder.set(columnName, dt);
        throw new IllegalArgumentException(
                "Unsupported value type for INSERT set(): " + value.getClass().getName()
                        + " (column: " + columnName + ")."
                        + " Supported types: String, Number, Boolean, LocalDate, LocalDateTime.");
    }

    /**
     * Dispatches {@code updateBuilder.set(column, value)} to the correct typed overload.
     * Null values are skipped (the column will not be included in the UPDATE SET clause).
     *
     * @param builder    the update builder
     * @param columnName the column name
     * @param value      the value to set
     * @return the builder after applying set (or unchanged if value is null)
     */
    public static UpdateBuilder set(UpdateBuilder builder, String columnName, Object value) {
        if (value == null) return builder;
        if (value instanceof String s) return builder.set(columnName, s);
        if (value instanceof Number n) return builder.set(columnName, n);
        if (value instanceof Boolean b) return builder.set(columnName, b);
        if (value instanceof LocalDate d) return builder.set(columnName, d);
        if (value instanceof LocalDateTime dt) return builder.set(columnName, dt);
        throw new IllegalArgumentException(
                "Unsupported value type for UPDATE set(): " + value.getClass().getName()
                        + " (column: " + columnName + ")."
                        + " Supported types: String, Number, Boolean, LocalDate, LocalDateTime.");
    }
}
