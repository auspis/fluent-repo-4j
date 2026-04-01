package io.github.auspis.fluentrepo4j.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.auspis.fluentsql4j.dsl.clause.SupportsWhere;
import io.github.auspis.fluentsql4j.dsl.clause.WhereConditionBuilder;
import io.github.auspis.fluentsql4j.dsl.insert.InsertBuilder;
import io.github.auspis.fluentsql4j.dsl.update.UpdateBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DslTypeDispatcher}.
 * Exercises all switch branches: String, Number, Boolean, LocalDate, LocalDateTime, null, default.
 */
class DslTypeDispatcherTest {

    interface TestWhere extends SupportsWhere<TestWhere> {}

    // ---- eq() ----

    @Nested
    class EqDispatch {

        @SuppressWarnings("unchecked")
        private WhereConditionBuilder<TestWhere> mockColumn() {
            WhereConditionBuilder<TestWhere> column = mock(WhereConditionBuilder.class);
            TestWhere sentinel = mock(TestWhere.class);
            when(column.eq(any(String.class))).thenReturn(sentinel);
            when(column.eq(any(Number.class))).thenReturn(sentinel);
            when(column.eq(any(Boolean.class))).thenReturn(sentinel);
            when(column.eq(any(LocalDate.class))).thenReturn(sentinel);
            when(column.eq(any(LocalDateTime.class))).thenReturn(sentinel);
            return column;
        }

        @Test
        void dispatches_string() {
            WhereConditionBuilder<TestWhere> column = mockColumn();
            DslTypeDispatcher.eq(column, "hello");
            verify(column).eq("hello");
        }

        @Test
        void dispatches_number() {
            WhereConditionBuilder<TestWhere> column = mockColumn();
            DslTypeDispatcher.eq(column, 42);
            verify(column).eq(42);
        }

        @Test
        void dispatches_boolean() {
            WhereConditionBuilder<TestWhere> column = mockColumn();
            DslTypeDispatcher.eq(column, true);
            verify(column).eq(true);
        }

        @Test
        void dispatches_localDate() {
            WhereConditionBuilder<TestWhere> column = mockColumn();
            LocalDate date = LocalDate.of(2024, 6, 15);
            DslTypeDispatcher.eq(column, date);
            verify(column).eq(date);
        }

        @Test
        void dispatches_localDateTime() {
            WhereConditionBuilder<TestWhere> column = mockColumn();
            LocalDateTime dateTime = LocalDateTime.of(2024, 6, 15, 10, 30);
            DslTypeDispatcher.eq(column, dateTime);
            verify(column).eq(dateTime);
        }

        @Test
        void null_throws_illegalArgument() {
            @SuppressWarnings("unchecked")
            WhereConditionBuilder<TestWhere> column = mock(WhereConditionBuilder.class);
            assertThatThrownBy(() -> DslTypeDispatcher.eq(column, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null");
        }

        @Test
        void unsupported_type_throws_illegalArgument() {
            @SuppressWarnings("unchecked")
            WhereConditionBuilder<TestWhere> column = mock(WhereConditionBuilder.class);
            Object obj = new Object();
            assertThatThrownBy(() -> DslTypeDispatcher.eq(column, obj))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported value type for eq()");
        }
    }

    // ---- set(InsertBuilder) ----

    @Nested
    class InsertSetDispatch {

        private InsertBuilder mockInsert() {
            InsertBuilder builder = mock(InsertBuilder.class);
            when(builder.set(any(String.class), any(String.class))).thenReturn(builder);
            when(builder.set(any(String.class), any(Number.class))).thenReturn(builder);
            when(builder.set(any(String.class), any(Boolean.class))).thenReturn(builder);
            when(builder.set(any(String.class), any(LocalDate.class))).thenReturn(builder);
            when(builder.set(any(String.class), any(LocalDateTime.class))).thenReturn(builder);
            return builder;
        }

        @Test
        void null_returns_builder_unchanged() {
            InsertBuilder builder = mockInsert();
            InsertBuilder result = DslTypeDispatcher.set(builder, "col", null);
            assertThat(result).isSameAs(builder);
        }

        @Test
        void dispatches_localDate() {
            InsertBuilder builder = mockInsert();
            LocalDate date = LocalDate.of(2024, 6, 15);
            DslTypeDispatcher.set(builder, "col", date);
            verify(builder).set("col", date);
        }

        @Test
        void dispatches_localDateTime() {
            InsertBuilder builder = mockInsert();
            LocalDateTime dateTime = LocalDateTime.of(2024, 6, 15, 10, 30);
            DslTypeDispatcher.set(builder, "col", dateTime);
            verify(builder).set("col", dateTime);
        }

        @Test
        void dispatches_boolean() {
            InsertBuilder builder = mockInsert();
            DslTypeDispatcher.set(builder, "col", true);
            verify(builder).set("col", true);
        }

        @Test
        void unsupported_type_throws_illegalArgument() {
            InsertBuilder builder = mockInsert();
            Object obj = new Object();
            assertThatThrownBy(() -> DslTypeDispatcher.set(builder, "col", obj))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported value type for INSERT set()");
        }
    }

    // ---- set(UpdateBuilder) ----

    @Nested
    class UpdateSetDispatch {

        private UpdateBuilder mockUpdate() {
            UpdateBuilder builder = mock(UpdateBuilder.class);
            when(builder.set(any(String.class), any(String.class))).thenReturn(builder);
            when(builder.set(any(String.class), any(Number.class))).thenReturn(builder);
            when(builder.set(any(String.class), any(Boolean.class))).thenReturn(builder);
            when(builder.set(any(String.class), any(LocalDate.class))).thenReturn(builder);
            when(builder.set(any(String.class), any(LocalDateTime.class))).thenReturn(builder);
            return builder;
        }

        @Test
        void null_returns_builder_unchanged() {
            UpdateBuilder builder = mockUpdate();
            UpdateBuilder result = DslTypeDispatcher.set(builder, "col", null);
            assertThat(result).isSameAs(builder);
        }

        @Test
        void dispatches_localDate() {
            UpdateBuilder builder = mockUpdate();
            LocalDate date = LocalDate.of(2024, 6, 15);
            DslTypeDispatcher.set(builder, "col", date);
            verify(builder).set("col", date);
        }

        @Test
        void dispatches_localDateTime() {
            UpdateBuilder builder = mockUpdate();
            LocalDateTime dateTime = LocalDateTime.of(2024, 6, 15, 10, 30);
            DslTypeDispatcher.set(builder, "col", dateTime);
            verify(builder).set("col", dateTime);
        }

        @Test
        void dispatches_boolean() {
            UpdateBuilder builder = mockUpdate();
            DslTypeDispatcher.set(builder, "col", false);
            verify(builder).set("col", false);
        }

        @Test
        void unsupported_type_throws_illegalArgument() {
            UpdateBuilder builder = mockUpdate();
            Object obj = new Object();
            assertThatThrownBy(() -> DslTypeDispatcher.set(builder, "col", obj))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported value type for UPDATE set()");
        }
    }
}
