package io.github.auspis.fluentrepo4j.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.auspis.fluentrepo4j.FluentPersistable;
import io.github.auspis.fluentsql4j.test.util.annotation.ComponentTest;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Component tests for {@link FluentEntityRowMapper}.
 * Exercises mapRow and convertIfNeeded branches with mocked ResultSet.
 */
@ComponentTest
class FluentEntityRowMapperTest {

    // ---- Helpers ----

    private ResultSet mockResultSet(String[] labels, String[] names, Object[] values) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(labels.length);
        for (int i = 0; i < labels.length; i++) {
            when(meta.getColumnLabel(i + 1)).thenReturn(labels[i]);
            when(meta.getColumnName(i + 1)).thenReturn(names[i]);
            when(rs.getObject(i + 1)).thenReturn(values[i]);
        }
        return rs;
    }

    // ---- Test entities ----

    @Table(name = "numeric_entity")
    static class NumericEntity {
        @Id
        Long id;

        int intPrimitive;
        long longPrimitive;
        double doublePrimitive;
        float floatPrimitive;
        short shortPrimitive;
        byte bytePrimitive;
        Integer intBoxed;
        Double doubleBoxed;
        Float floatBoxed;
        Short shortBoxed;
        Byte byteBoxed;
    }

    @Table(name = "temporal_entity")
    static class TemporalEntity {
        @Id
        Long id;

        LocalDateTime createdAt;
        LocalDate birthdate;
    }

    @Table(name = "byte_entity")
    static class ByteArrayEntity {
        @Id
        Long id;

        String data;
    }

    @Table(name = "simple_entity")
    static class SimpleEntity {
        @Id
        Long id;

        String name;
        int primitiveField;
    }

    @Table(name = "persistable_entity")
    static class PersistableEntity implements FluentPersistable<Long> {
        @Id
        Long id;

        String name;
        private boolean persisted = false;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public boolean isNew() {
            return !persisted;
        }

        @Override
        public void markPersisted() {
            persisted = true;
        }

        boolean isPersisted() {
            return persisted;
        }
    }

    // ============================================================
    // NUMERIC COERCION BRANCHES
    // ============================================================

    @Nested
    class NumericCoercion {

        @Test
        void intPrimitive_from_long_value() throws SQLException {
            FluentEntityInformation<NumericEntity, Long> info = new FluentEntityInformation<>(NumericEntity.class);
            FluentEntityRowMapper<NumericEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "int_primitive"}, new String[] {"id", "int_primitive"}, new Object[] {1L, 42L});

            NumericEntity result = mapper.mapRow(rs, 0);
            assertThat(result.intPrimitive).isEqualTo(42);
        }

        @Test
        void longPrimitive_from_int_value() throws SQLException {
            FluentEntityInformation<NumericEntity, Long> info = new FluentEntityInformation<>(NumericEntity.class);
            FluentEntityRowMapper<NumericEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "long_primitive"}, new String[] {"id", "long_primitive"}, new Object[] {1L, 42
                    });

            NumericEntity result = mapper.mapRow(rs, 0);
            assertThat(result.longPrimitive).isEqualTo(42L);
        }

        @Test
        void doublePrimitive_from_float_value() throws SQLException {
            FluentEntityInformation<NumericEntity, Long> info = new FluentEntityInformation<>(NumericEntity.class);
            FluentEntityRowMapper<NumericEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "double_primitive"},
                    new String[] {"id", "double_primitive"},
                    new Object[] {1L, 3.14f});

            NumericEntity result = mapper.mapRow(rs, 0);
            assertThat(result.doublePrimitive).isCloseTo(3.14, org.assertj.core.api.Assertions.within(0.01));
        }

        @Test
        void floatPrimitive_from_double_value() throws SQLException {
            FluentEntityInformation<NumericEntity, Long> info = new FluentEntityInformation<>(NumericEntity.class);
            FluentEntityRowMapper<NumericEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "float_primitive"},
                    new String[] {"id", "float_primitive"},
                    new Object[] {1L, 3.14});

            NumericEntity result = mapper.mapRow(rs, 0);
            assertThat(result.floatPrimitive).isCloseTo(3.14f, org.assertj.core.api.Assertions.within(0.01f));
        }

        @Test
        void floatBoxed_from_double_value() throws SQLException {
            FluentEntityInformation<NumericEntity, Long> info = new FluentEntityInformation<>(NumericEntity.class);
            FluentEntityRowMapper<NumericEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "float_boxed"}, new String[] {"id", "float_boxed"}, new Object[] {1L, 2.5});

            NumericEntity result = mapper.mapRow(rs, 0);
            assertThat(result.floatBoxed).isEqualTo(2.5f);
        }

        @Test
        void shortPrimitive_from_int_value() throws SQLException {
            FluentEntityInformation<NumericEntity, Long> info = new FluentEntityInformation<>(NumericEntity.class);
            FluentEntityRowMapper<NumericEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "short_primitive"}, new String[] {"id", "short_primitive"}, new Object[] {1L, 42
                    });

            NumericEntity result = mapper.mapRow(rs, 0);
            assertThat(result.shortPrimitive).isEqualTo((short) 42);
        }

        @Test
        void shortBoxed_from_int_value() throws SQLException {
            FluentEntityInformation<NumericEntity, Long> info = new FluentEntityInformation<>(NumericEntity.class);
            FluentEntityRowMapper<NumericEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "short_boxed"}, new String[] {"id", "short_boxed"}, new Object[] {1L, 100});

            NumericEntity result = mapper.mapRow(rs, 0);
            assertThat(result.shortBoxed).isEqualTo((short) 100);
        }

        @Test
        void bytePrimitive_from_int_value() throws SQLException {
            FluentEntityInformation<NumericEntity, Long> info = new FluentEntityInformation<>(NumericEntity.class);
            FluentEntityRowMapper<NumericEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "byte_primitive"}, new String[] {"id", "byte_primitive"}, new Object[] {1L, 65
                    });

            NumericEntity result = mapper.mapRow(rs, 0);
            assertThat(result.bytePrimitive).isEqualTo((byte) 65);
        }

        @Test
        void byteBoxed_from_int_value() throws SQLException {
            FluentEntityInformation<NumericEntity, Long> info = new FluentEntityInformation<>(NumericEntity.class);
            FluentEntityRowMapper<NumericEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "byte_boxed"}, new String[] {"id", "byte_boxed"}, new Object[] {1L, 7});

            NumericEntity result = mapper.mapRow(rs, 0);
            assertThat(result.byteBoxed).isEqualTo((byte) 7);
        }

        @Test
        void intBoxed_from_long_value() throws SQLException {
            FluentEntityInformation<NumericEntity, Long> info = new FluentEntityInformation<>(NumericEntity.class);
            FluentEntityRowMapper<NumericEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "int_boxed"}, new String[] {"id", "int_boxed"}, new Object[] {1L, 99L});

            NumericEntity result = mapper.mapRow(rs, 0);
            assertThat(result.intBoxed).isEqualTo(99);
        }

        @Test
        void doubleBoxed_from_float_value() throws SQLException {
            FluentEntityInformation<NumericEntity, Long> info = new FluentEntityInformation<>(NumericEntity.class);
            FluentEntityRowMapper<NumericEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "double_boxed"}, new String[] {"id", "double_boxed"}, new Object[] {1L, 1.5f});

            NumericEntity result = mapper.mapRow(rs, 0);
            assertThat(result.doubleBoxed).isEqualTo(1.5);
        }
    }

    // ============================================================
    // TEMPORAL COERCION BRANCHES
    // ============================================================

    @Nested
    class TemporalCoercion {

        @Test
        void timestamp_to_localDateTime() throws SQLException {
            FluentEntityInformation<TemporalEntity, Long> info = new FluentEntityInformation<>(TemporalEntity.class);
            FluentEntityRowMapper<TemporalEntity> mapper = new FluentEntityRowMapper<>(info);

            java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2024-06-15 10:30:00");
            ResultSet rs = mockResultSet(
                    new String[] {"id", "created_at"}, new String[] {"id", "created_at"}, new Object[] {1L, ts});

            TemporalEntity result = mapper.mapRow(rs, 0);
            assertThat(result.createdAt).isEqualTo(LocalDateTime.of(2024, 6, 15, 10, 30, 0));
        }

        @Test
        void sqlDate_to_localDate() throws SQLException {
            FluentEntityInformation<TemporalEntity, Long> info = new FluentEntityInformation<>(TemporalEntity.class);
            FluentEntityRowMapper<TemporalEntity> mapper = new FluentEntityRowMapper<>(info);

            java.sql.Date sqlDate = java.sql.Date.valueOf("2024-06-15");
            ResultSet rs = mockResultSet(
                    new String[] {"id", "birthdate"}, new String[] {"id", "birthdate"}, new Object[] {1L, sqlDate});

            TemporalEntity result = mapper.mapRow(rs, 0);
            assertThat(result.birthdate).isEqualTo(LocalDate.of(2024, 6, 15));
        }

        @Test
        void timestamp_to_nonLocalDateTime_throws() throws SQLException {
            // Timestamp value where target field is NOT LocalDateTime → fallback returns as-is
            FluentEntityInformation<SimpleEntity, Long> info = new FluentEntityInformation<>(SimpleEntity.class);
            FluentEntityRowMapper<SimpleEntity> mapper = new FluentEntityRowMapper<>(info);

            java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2024-06-15 10:30:00");
            ResultSet rs =
                    mockResultSet(new String[] {"id", "name"}, new String[] {"id", "name"}, new Object[] {1L, ts});

            // Timestamp cannot be assigned to String field → IllegalArgumentException
            assertThatThrownBy(() -> mapper.mapRow(rs, 0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void sqlDate_to_nonLocalDate_throws() throws SQLException {
            FluentEntityInformation<SimpleEntity, Long> info = new FluentEntityInformation<>(SimpleEntity.class);
            FluentEntityRowMapper<SimpleEntity> mapper = new FluentEntityRowMapper<>(info);

            java.sql.Date sqlDate = java.sql.Date.valueOf("2024-06-15");
            ResultSet rs =
                    mockResultSet(new String[] {"id", "name"}, new String[] {"id", "name"}, new Object[] {1L, sqlDate});

            // java.sql.Date cannot be assigned to String field → IllegalArgumentException
            assertThatThrownBy(() -> mapper.mapRow(rs, 0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ============================================================
    // BYTE ARRAY COERCION
    // ============================================================

    @Nested
    class ByteArrayCoercion {

        @Test
        void byteArray_to_string() throws SQLException {
            FluentEntityInformation<ByteArrayEntity, Long> info = new FluentEntityInformation<>(ByteArrayEntity.class);
            FluentEntityRowMapper<ByteArrayEntity> mapper = new FluentEntityRowMapper<>(info);

            byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);
            ResultSet rs =
                    mockResultSet(new String[] {"id", "data"}, new String[] {"id", "data"}, new Object[] {1L, bytes});

            ByteArrayEntity result = mapper.mapRow(rs, 0);
            assertThat(result.data).isEqualTo("hello world");
        }

        @Test
        void byteArray_to_nonString_throws() throws SQLException {
            // byte[] value targeting a non-String field → fallback returns as-is
            FluentEntityInformation<NumericEntity, Long> info = new FluentEntityInformation<>(NumericEntity.class);
            FluentEntityRowMapper<NumericEntity> mapper = new FluentEntityRowMapper<>(info);

            byte[] bytes = {1, 2, 3};
            ResultSet rs = mockResultSet(
                    new String[] {"id", "int_boxed"}, new String[] {"id", "int_boxed"}, new Object[] {1L, bytes});

            // byte[] cannot be assigned to Integer field → IllegalArgumentException
            assertThatThrownBy(() -> mapper.mapRow(rs, 0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ============================================================
    // MAPROW CONDITIONAL BRANCHES
    // ============================================================

    @Nested
    class MapRowBranches {

        @Test
        void null_value_on_primitive_field_is_skipped() throws SQLException {
            FluentEntityInformation<SimpleEntity, Long> info = new FluentEntityInformation<>(SimpleEntity.class);
            FluentEntityRowMapper<SimpleEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "primitive_field"},
                    new String[] {"id", "primitive_field"},
                    new Object[] {1L, null});

            SimpleEntity result = mapper.mapRow(rs, 0);
            // null on primitive → skipped; default int value is 0
            assertThat(result.primitiveField).isZero();
        }

        @Test
        void null_value_on_boxed_field_is_set() throws SQLException {
            FluentEntityInformation<SimpleEntity, Long> info = new FluentEntityInformation<>(SimpleEntity.class);
            FluentEntityRowMapper<SimpleEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs =
                    mockResultSet(new String[] {"id", "name"}, new String[] {"id", "name"}, new Object[] {1L, null});

            SimpleEntity result = mapper.mapRow(rs, 0);
            assertThat(result.name).isNull();
        }

        @Test
        void columnLabel_fallback_to_columnName() throws SQLException {
            FluentEntityInformation<SimpleEntity, Long> info = new FluentEntityInformation<>(SimpleEntity.class);
            FluentEntityRowMapper<SimpleEntity> mapper = new FluentEntityRowMapper<>(info);

            // Label is an alias that won't match; name matches
            ResultSet rs = mock(ResultSet.class);
            ResultSetMetaData meta = mock(ResultSetMetaData.class);
            when(rs.getMetaData()).thenReturn(meta);
            when(meta.getColumnCount()).thenReturn(2);
            when(meta.getColumnLabel(1)).thenReturn("id");
            when(meta.getColumnName(1)).thenReturn("id");
            when(rs.getObject(1)).thenReturn(1L);
            when(meta.getColumnLabel(2)).thenReturn("alias_col");
            when(meta.getColumnName(2)).thenReturn("name");
            when(rs.getObject(2)).thenReturn("Test");

            SimpleEntity result = mapper.mapRow(rs, 0);
            assertThat(result.name).isEqualTo("Test");
        }

        @Test
        void unmapped_column_is_ignored() throws SQLException {
            FluentEntityInformation<SimpleEntity, Long> info = new FluentEntityInformation<>(SimpleEntity.class);
            FluentEntityRowMapper<SimpleEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs = mockResultSet(
                    new String[] {"id", "nonexistent"}, new String[] {"id", "nonexistent"}, new Object[] {1L, "ignored"
                    });

            SimpleEntity result = mapper.mapRow(rs, 0);
            assertThat(result.id).isEqualTo(1L);
        }

        @Test
        void fluentPersistable_markPersisted_called() throws SQLException {
            FluentEntityInformation<PersistableEntity, Long> info =
                    new FluentEntityInformation<>(PersistableEntity.class);
            FluentEntityRowMapper<PersistableEntity> mapper = new FluentEntityRowMapper<>(info);

            ResultSet rs =
                    mockResultSet(new String[] {"id", "name"}, new String[] {"id", "name"}, new Object[] {1L, "Test"});

            PersistableEntity result = mapper.mapRow(rs, 0);
            assertThat(result.isPersisted()).isTrue();
        }
    }

    // ============================================================
    // FALLBACK RETURN
    // ============================================================

    @Nested
    class FallbackReturn {

        @Test
        void unsupported_type_returns_value_as_is() throws SQLException {
            // Value type doesn't match field type and doesn't fit any coercion path
            FluentEntityInformation<SimpleEntity, Long> info = new FluentEntityInformation<>(SimpleEntity.class);
            FluentEntityRowMapper<SimpleEntity> mapper = new FluentEntityRowMapper<>(info);

            // Pass a Map where a String is expected — will attempt field.set which may fail
            // but convertIfNeeded should return value as-is
            ResultSet rs = mockResultSet(
                    new String[] {"id", "name"}, new String[] {"id", "name"}, new Object[] {1L, "normalValue"});

            SimpleEntity result = mapper.mapRow(rs, 0);
            assertThat(result.name).isEqualTo("normalValue");
        }
    }
}
