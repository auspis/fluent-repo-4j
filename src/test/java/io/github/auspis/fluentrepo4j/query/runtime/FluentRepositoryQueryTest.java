package io.github.auspis.fluentrepo4j.query.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.query.OrderByClause;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.ast.dql.clause.Sorting;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.query.QueryMethod;

class FluentRepositoryQueryTest {

    @Test
    void getQueryMethod_exposes_underlying_method() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor("findByName", String.class);

        assertThat(query.getQueryMethod().getReturnedObjectType()).isEqualTo(User.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void adaptDeleteResult_supports_boxed_numeric_return_types() throws Exception {
        FluentRepositoryQuery<User, Long> longDeleteQuery = queryFor("findByName", String.class);
        FluentRepositoryQuery<User, Long> intDeleteQuery = queryFor("findByName", String.class);

        QueryMethod longMethod = mock(QueryMethod.class);
        QueryMethod intMethod = mock(QueryMethod.class);
        when(longMethod.getReturnedObjectType()).thenReturn((Class) Long.class);
        when(intMethod.getReturnedObjectType()).thenReturn((Class) Integer.class);
        setPrivateField(longDeleteQuery, "queryMethod", longMethod);
        setPrivateField(intDeleteQuery, "queryMethod", intMethod);

        Object longResult = invokePrivate(longDeleteQuery, "adaptDeleteResult", new Class[] {int.class}, 3);
        Object intResult = invokePrivate(intDeleteQuery, "adaptDeleteResult", new Class[] {int.class}, 4);

        assertThat(longResult).isEqualTo(3L);
        assertThat(intResult).isEqualTo(4);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void adaptSelectResult_stream_query_returns_stream_instance() throws Exception {
        FluentRepositoryQuery<User, Long> streamQuery = queryFor("findByName", String.class);
        List<User> rows = List.of(userWithId(10L, "A", "a@test", 30, true));

        QueryMethod streamMethod = mock(QueryMethod.class);
        when(streamMethod.isPageQuery()).thenReturn(false);
        when(streamMethod.isSliceQuery()).thenReturn(false);
        when(streamMethod.isCollectionQuery()).thenReturn(true);
        when(streamMethod.isStreamQuery()).thenReturn(false);
        when(streamMethod.getReturnedObjectType()).thenReturn((Class) Stream.class);
        setPrivateField(streamQuery, "queryMethod", streamMethod);

        Object result = invokePrivate(
                streamQuery, "adaptSelectResult", new Class[] {List.class, Object[].class}, rows, new Object[0]);

        assertThat(result).isInstanceOf(Stream.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void adaptSelectResult_optional_query_handles_empty_and_present() throws Exception {
        FluentRepositoryQuery<User, Long> optionalQuery = queryFor("findByName", String.class);
        User user = userWithId(11L, "B", "b@test", 31, true);

        QueryMethod optionalMethod = mock(QueryMethod.class);
        when(optionalMethod.isPageQuery()).thenReturn(false);
        when(optionalMethod.isSliceQuery()).thenReturn(false);
        when(optionalMethod.isCollectionQuery()).thenReturn(false);
        when(optionalMethod.isStreamQuery()).thenReturn(false);
        when(optionalMethod.getReturnedObjectType()).thenReturn((Class) Optional.class);
        setPrivateField(optionalQuery, "queryMethod", optionalMethod);

        Object empty = invokePrivate(
                optionalQuery, "adaptSelectResult", new Class[] {List.class, Object[].class}, List.of(), new Object[0]);
        Object present = invokePrivate(
                optionalQuery,
                "adaptSelectResult",
                new Class[] {List.class, Object[].class},
                List.of(user),
                new Object[0]);

        assertThat(empty).isEqualTo(Optional.empty());
        assertThat(present).isEqualTo(Optional.of(user));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void adaptSelectResult_single_entity_returns_null_or_first_element() throws Exception {
        FluentRepositoryQuery<User, Long> entityQuery = queryFor("findByName", String.class);
        User user = userWithId(12L, "C", "c@test", 32, false);

        QueryMethod singleResultMethod = mock(QueryMethod.class);
        when(singleResultMethod.isPageQuery()).thenReturn(false);
        when(singleResultMethod.isSliceQuery()).thenReturn(false);
        when(singleResultMethod.isCollectionQuery()).thenReturn(false);
        when(singleResultMethod.isStreamQuery()).thenReturn(false);
        when(singleResultMethod.getReturnedObjectType()).thenReturn((Class) User.class);
        setPrivateField(entityQuery, "queryMethod", singleResultMethod);

        Object empty = invokePrivate(
                entityQuery, "adaptSelectResult", new Class[] {List.class, Object[].class}, List.of(), new Object[0]);
        Object first = invokePrivate(
                entityQuery,
                "adaptSelectResult",
                new Class[] {List.class, Object[].class},
                List.of(user, userWithId(13L, "D", "d@test", 33, true)),
                new Object[0]);

        assertThat(empty).isNull();
        assertThat(first).isEqualTo(user);
    }

    @Test
    void adaptAsSlice_uses_unpaged_when_runtime_pageable_is_missing() throws Exception {
        FluentRepositoryQuery<User, Long> sliceQuery = queryFor("findByAgeGreaterThan", Integer.class, Pageable.class);
        List<User> rows = List.of(userWithId(14L, "E", "e@test", 34, true));

        @SuppressWarnings("unchecked")
        Slice<User> slice = (Slice<User>)
                invokePrivate(sliceQuery, "adaptAsSlice", new Class[] {List.class, Object[].class}, rows, (Object)
                        new Object[] {30, "notPageable"});

        assertThat(slice.getPageable().isUnpaged()).isTrue();
        assertThat(slice.hasNext()).isFalse();
    }

    @Test
    void extractPageable_returns_null_for_non_pageable_argument() throws Exception {
        FluentRepositoryQuery<User, Long> pageQuery = queryFor("findByActive", Boolean.class, Pageable.class);

        Pageable pageable = invokePrivate(pageQuery, "extractPageable", new Class[] {Object[].class}, (Object)
                new Object[] {true, "notPageable"});

        assertThat(pageable).isNull();
    }

    @Test
    void sort_uses_sort_parameter_when_present_and_returns_null_for_wrong_type() throws Exception {
        FluentRepositoryQuery<User, Long> sortQuery = queryFor("findByName", String.class, Sort.class);
        Sort desc = Sort.by(Sort.Order.desc("name"));

        Sort resolved =
                invokePrivate(sortQuery, "sort", new Class[] {Object[].class}, (Object) new Object[] {"Alice", desc});
        Sort unresolved = invokePrivate(
                sortQuery, "sort", new Class[] {Object[].class}, (Object) new Object[] {"Alice", "notSort"});

        assertThat(resolved).isEqualTo(desc);
        assertThat(unresolved).isNull();
    }

    @Test
    void orderByClauses_maps_descending_direction() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor("findByName", String.class);

        @SuppressWarnings("unchecked")
        List<OrderByClause> clauses = (List<OrderByClause>)
                invokePrivate(query, "orderByClauses", new Class[] {Sort.class}, Sort.by(Sort.Order.desc("age")));

        assertThat(clauses).hasSize(1);
        assertThat(clauses.get(0).columnName()).isEqualTo("age");
        assertThat(clauses.get(0).direction()).isEqualTo(Sorting.SortOrder.DESC);
    }

    @Test
    void pageWindow_returns_null_for_unpaged_pageable() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor("findByActive", Boolean.class, Pageable.class);

        Object pageWindow = invokePrivate(
                query, "pageWindow", new Class[] {Object[].class}, (Object) new Object[] {true, Pageable.unpaged()});

        assertThat(pageWindow).isNull();
    }

    @Test
    void columnName_falls_back_to_input_for_unknown_property() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor("findByName", String.class);

        String resolved = invokePrivate(query, "columnName", new Class[] {String.class}, "missingProperty");

        assertThat(resolved).isEqualTo("missingProperty");
    }

    interface ProbeRepository extends org.springframework.data.repository.CrudRepository<User, Long> {

        List<User> findByName(String name);

        Stream<User> findByEmail(String email);

        Optional<User> findFirstByName(String name);

        User findFirstByActive(Boolean active);

        Page<User> findByActive(Boolean active, Pageable pageable);

        Slice<User> findByAgeGreaterThan(Integer age, Pageable pageable);

        List<User> findByName(String name, Sort sort);

        Long deleteByName(String name);

        Integer deleteByAge(Integer age);
    }

    private FluentRepositoryQuery<User, Long> queryFor(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ProbeRepository.class.getMethod(methodName, parameterTypes);
        RepositoryMetadata metadata = new DefaultRepositoryMetadata(ProbeRepository.class);
        SpelAwareProxyProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
        FluentEntityInformation<User, Long> entityInformation = new FluentEntityInformation<>(User.class);
        FluentConnectionProvider connectionProvider = mock(FluentConnectionProvider.class);
        DSL dsl = mock(DSL.class);

        return new FluentRepositoryQuery<>(
                method, metadata, projectionFactory, entityInformation, connectionProvider, dsl);
    }

    private User userWithId(Long id, String name, String email, Integer age, Boolean active) {
        LocalDate birthdate = LocalDate.now().minusYears(age);
        LocalDateTime createdAt = LocalDateTime.now();
        return new User(id, name, email, age, active, birthdate, createdAt, "Unknown", "{}");
    }

    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <R> R invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return (R) method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
