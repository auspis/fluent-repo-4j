package io.github.auspis.fluentrepo4j.query.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.functional.FunctionalCrudRepository;
import io.github.auspis.fluentrepo4j.functional.read.FunctionalReadRepository;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Error;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Found;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.NotFound;
import io.github.auspis.fluentrepo4j.functional.write.FunctionalWriteRepository;
import io.github.auspis.fluentrepo4j.functional.write.WriteResult;
import io.github.auspis.fluentrepo4j.functional.write.WriteResult.Success;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.query.OrderByClause;
import io.github.auspis.fluentrepo4j.query.PageWindow;
import io.github.auspis.fluentrepo4j.query.QueryRuntimeParams;
import io.github.auspis.fluentrepo4j.query.mapper.dsl.MappedQueryStrategy;
import io.github.auspis.fluentrepo4j.query.runtime.FluentRepositoryQueryTest.FoundReadFunctionalCase.ListEntityFoundReadFunctionalCase;
import io.github.auspis.fluentrepo4j.query.runtime.FluentRepositoryQueryTest.FoundReadFunctionalCase.SingleEntityFoundReadFunctionalCase;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.delete.DeleteBuilder;
import io.github.auspis.fluentsql4j.dsl.select.SelectBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;

class FluentRepositoryQueryTest {

    interface ReadProbeRepository extends FunctionalReadRepository<User, Long> {
        ReadResult<User> findByEmail(String email);

        ReadResult<List<User>> findByName(String name);

        ReadResult<Stream<User>> findStreamByName(String name);

        ReadResult<Long> countByActive(Boolean active);

        ReadResult<Boolean> existsByEmail(String email);
    }

    interface InvalidReadProbeRepository extends FunctionalReadRepository<User, Long> {
        ReadResult<java.util.Optional<User>> findByEmail(String email);
    }

    interface WriteProbeRepository extends FunctionalWriteRepository<User, Long> {
        WriteResult<Long> deleteByEmail(String email);

        WriteResult<Boolean> deleteByName(String name);
    }

    interface CrudProbeRepository extends FunctionalCrudRepository<User, Long> {
        ReadResult<User> findByEmail(String email);

        WriteResult<Long> deleteByEmail(String email);
    }

    interface InvalidWriteProbeRepository extends FunctionalWriteRepository<User, Long> {
        WriteResult<User> findByEmail(String email);
    }

    interface InvalidReadCountProbeRepository extends FunctionalReadRepository<User, Long> {
        ReadResult<Integer> countByActive(Boolean active);
    }

    interface InvalidReadExistsProbeRepository extends FunctionalReadRepository<User, Long> {
        ReadResult<Long> existsByEmail(String email);
    }

    interface StandardProbeRepository extends Repository<User, Long> {
        User findByEmail(String email);

        List<User> findAllByName(String name);

        Stream<User> findStreamByName(String name);

        Optional<User> findFirstByEmail(String email);

        long countByActive(Boolean active);

        boolean existsByEmail(String email);

        long deleteByEmail(String email);

        int deleteByName(String name);

        void deleteByActive(Boolean active);

        List<User> findByName(String name, Sort sort);

        List<User> findByName(String name, Pageable pageable);
    }

    interface PageProbeRepository extends Repository<User, Long> {
        Page<User> findByName(String name, Pageable pageable);
    }

    interface SliceProbeRepository extends Repository<User, Long> {
        Slice<User> findByName(String name, Pageable pageable);
    }

    @Test
    void validReadWrapperConstructs() {
        assertDoesNotThrow(() -> queryFor(ReadProbeRepository.class, "findByEmail", String.class));
        assertDoesNotThrow(() -> queryFor(ReadProbeRepository.class, "findByName", String.class));
        assertDoesNotThrow(() -> queryFor(ReadProbeRepository.class, "countByActive", Boolean.class));
        assertDoesNotThrow(() -> queryFor(ReadProbeRepository.class, "existsByEmail", String.class));
    }

    @Test
    void readWrapperRejectsOptionalInnerType() {
        assertThatThrownBy(() -> queryFor(InvalidReadProbeRepository.class, "findByEmail", String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ReadResult does not support Optional inner types");
    }

    @Test
    void readWrapperRejectsInvalidCountAndExistsInnerTypes() {
        assertThatThrownBy(() -> queryFor(InvalidReadCountProbeRepository.class, "countByActive", Boolean.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported ReadResult inner type for count-derived query");

        assertThatThrownBy(() -> queryFor(InvalidReadExistsProbeRepository.class, "existsByEmail", String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported ReadResult inner type for exists-derived query");
    }

    @Test
    void writeWrapperRejectsNonDeleteDerivedMethods() {
        assertThatThrownBy(() -> queryFor(InvalidWriteProbeRepository.class, "findByEmail", String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WriteResult is supported only for delete-derived query methods");
    }

    @Test
    void validCrudWrapperConstructs() {
        assertDoesNotThrow(() -> queryFor(CrudProbeRepository.class, "findByEmail", String.class));
        assertDoesNotThrow(() -> queryFor(CrudProbeRepository.class, "deleteByEmail", String.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"findByEmail", "findByName", "findStreamByName"})
    void adaptReadSingleResultFunctionalOnResultWithEmptyInputReturnsNotFound(String repositoryMethod)
            throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(ReadProbeRepository.class, repositoryMethod, String.class);

        Object result = invoke(
                query,
                "adaptSelectResultReadFunctional",
                new Class[] {List.class, Object[].class},
                List.of(),
                new Object[0]);

        assertThat(result).isInstanceOf(NotFound.class);
    }

    @ParameterizedTest
    @MethodSource("foundReadFunctionalCases")
    @SuppressWarnings("unchecked")
    void adaptReadSingleResultFunctionalOnResultWithOneElementReturnsFound(FoundReadFunctionalCase testCase)
            throws Exception {
        FluentRepositoryQuery<User, Long> query =
                queryFor(ReadProbeRepository.class, testCase.repositoryMethod(), String.class);

        Object result = invoke(
                query,
                "adaptSelectResultReadFunctional",
                new Class[] {List.class, Object[].class},
                List.of(testCase.user()),
                new Object[0]);

        switch (testCase) {
            case SingleEntityFoundReadFunctionalCase singleEntityCase ->
                assertThat(result)
                        .isInstanceOf(Found.class)
                        .extracting(r -> ((Found<User>) r).value())
                        .isEqualTo(singleEntityCase.user());
            case ListEntityFoundReadFunctionalCase listEntityCase ->
                assertThat(result)
                        .isInstanceOf(Found.class)
                        .extracting(r -> ((Found<List<User>>) r).value(), InstanceOfAssertFactories.LIST)
                        .hasSize(1)
                        .containsExactly(listEntityCase.user());
        }
    }

    private static Stream<FoundReadFunctionalCase> foundReadFunctionalCases() {
        return Stream.of(
                new SingleEntityFoundReadFunctionalCase("findByEmail", new User("User", "u@test.com").withId(10L)),
                new ListEntityFoundReadFunctionalCase("findByName", new User("List", "list@test.com").withId(11L)));
    }

    sealed interface FoundReadFunctionalCase {
        String repositoryMethod();

        User user();

        record SingleEntityFoundReadFunctionalCase(String repositoryMethod, User user)
                implements FoundReadFunctionalCase {}

        record ListEntityFoundReadFunctionalCase(String repositoryMethod, User user)
                implements FoundReadFunctionalCase {}
    }

    @Test
    void adaptWriteDeleteResultSupportsLongAndBoolean() throws Exception {
        FluentRepositoryQuery<User, Long> longDelete =
                queryFor(WriteProbeRepository.class, "deleteByEmail", String.class);
        FluentRepositoryQuery<User, Long> boolDelete =
                queryFor(WriteProbeRepository.class, "deleteByName", String.class);

        Object longResult = invoke(longDelete, "adaptDeleteResultWriteFunctional", new Class[] {int.class}, 3);
        Object boolResult = invoke(boolDelete, "adaptDeleteResultWriteFunctional", new Class[] {int.class}, 0);

        assertThat(longResult).isEqualTo(3L);
        assertThat(boolResult).isEqualTo(false);
    }

    @Test
    void getQueryMethodReturnsMethodMetadata() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(StandardProbeRepository.class, "findByEmail", String.class);

        assertThat(query.getQueryMethod().getName()).isEqualTo("findByEmail");
    }

    @Test
    void adaptDeleteResultSupportsLongIntegerAndVoid() throws Exception {
        FluentRepositoryQuery<User, Long> longDelete =
                queryFor(StandardProbeRepository.class, "deleteByEmail", String.class);
        FluentRepositoryQuery<User, Long> integerDelete =
                queryFor(StandardProbeRepository.class, "deleteByName", String.class);
        FluentRepositoryQuery<User, Long> voidDelete =
                queryFor(StandardProbeRepository.class, "deleteByActive", Boolean.class);

        Object longResult = invoke(longDelete, "adaptDeleteResult", new Class[] {int.class}, 4);
        Object integerResult = invoke(integerDelete, "adaptDeleteResult", new Class[] {int.class}, 5);
        Object voidResult = invoke(voidDelete, "adaptDeleteResult", new Class[] {int.class}, 1);

        assertThat(longResult).isEqualTo(4L);
        assertThat(integerResult).isEqualTo(5);
        assertThat(voidResult).isNull();
    }

    @Test
    void adaptSelectResultSupportsSingleCollectionStreamAndOptional() throws Exception {
        User user = new User("User", "user@test.com").withId(100L);

        FluentRepositoryQuery<User, Long> single = queryFor(StandardProbeRepository.class, "findByEmail", String.class);
        FluentRepositoryQuery<User, Long> collection =
                queryFor(StandardProbeRepository.class, "findAllByName", String.class);
        FluentRepositoryQuery<User, Long> stream =
                queryFor(StandardProbeRepository.class, "findStreamByName", String.class);
        FluentRepositoryQuery<User, Long> optional =
                queryFor(StandardProbeRepository.class, "findFirstByEmail", String.class);

        Object singleFound = invoke(
                single, "adaptSelectResult", new Class[] {List.class, Object[].class}, List.of(user), new Object[0]);
        Object singleEmpty =
                invoke(single, "adaptSelectResult", new Class[] {List.class, Object[].class}, List.of(), new Object[0]);
        Object collectionResult = invoke(
                collection,
                "adaptSelectResult",
                new Class[] {List.class, Object[].class},
                List.of(user),
                new Object[0]);
        Object streamResult = invoke(
                stream, "adaptSelectResult", new Class[] {List.class, Object[].class}, List.of(user), new Object[0]);
        Object optionalFound = invoke(
                optional, "adaptSelectResult", new Class[] {List.class, Object[].class}, List.of(user), new Object[0]);
        Object optionalEmpty = invoke(
                optional, "adaptSelectResult", new Class[] {List.class, Object[].class}, List.of(), new Object[0]);

        assertThat(singleFound).isEqualTo(user);
        assertThat(singleEmpty).isNull();
        assertThat(collectionResult).isInstanceOf(List.class);
        assertThat((List<User>) collectionResult).containsExactly(user);
        assertThat(streamResult).isInstanceOf(List.class);
        assertThat((List<User>) streamResult).containsExactly(user);
        assertThat(optionalFound).isEqualTo(user);
        assertThat(optionalEmpty).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void adaptReturnTypeReadFunctionalSupportsScalarAndCollectionPaths() throws Exception {
        User user = new User("User", "user@test.com").withId(101L);
        FluentRepositoryQuery<User, Long> countQuery =
                queryFor(ReadProbeRepository.class, "countByActive", Boolean.class);
        FluentRepositoryQuery<User, Long> entityQuery =
                queryFor(ReadProbeRepository.class, "findByEmail", String.class);

        Object countResult = invoke(
                countQuery,
                "adaptReturnTypeReadFunctional",
                new Class[] {Object.class, Object[].class},
                12L,
                new Object[0]);
        Object entityResult = invoke(
                entityQuery,
                "adaptReturnTypeReadFunctional",
                new Class[] {Object.class, Object[].class},
                List.of(user),
                new Object[0]);

        assertThat(countResult)
                .isInstanceOf(Found.class)
                .extracting(r -> ((Found<Long>) r).value())
                .isEqualTo(12L);
        assertThat(entityResult)
                .isInstanceOf(Found.class)
                .extracting(r -> ((Found<User>) r).value())
                .isEqualTo(user);
    }

    @Test
    @SuppressWarnings("unchecked")
    void adaptReturnTypeWriteFunctionalSupportsLongAndBooleanOutputs() throws Exception {
        FluentRepositoryQuery<User, Long> longDelete =
                queryFor(WriteProbeRepository.class, "deleteByEmail", String.class);
        FluentRepositoryQuery<User, Long> boolDelete =
                queryFor(WriteProbeRepository.class, "deleteByName", String.class);

        Object longResult = invoke(
                longDelete,
                "adaptReturnTypeWriteFunctional",
                new Class[] {Object.class, Object[].class},
                2,
                new Object[0]);
        Object boolResult = invoke(
                boolDelete,
                "adaptReturnTypeWriteFunctional",
                new Class[] {Object.class, Object[].class},
                0,
                new Object[0]);

        assertThat(longResult)
                .isInstanceOf(Success.class)
                .extracting(r -> ((Success<Long>) r).value())
                .isEqualTo(2L);
        assertThat(boolResult)
                .isInstanceOf(Success.class)
                .extracting(r -> ((Success<Boolean>) r).value())
                .isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void adaptReturnTypeWriteFunctionalSupportsCollectionPageSliceAndStream() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(WriteProbeRepository.class, "deleteByEmail", String.class);
        QueryExecutionResources<User> executionResources = mock(QueryExecutionResources.class);
        SelectBuilder selectBuilder = mock(SelectBuilder.class);
        Pageable pageable = PageRequest.of(0, 2);
        User user = new User("W", "w@test.com").withId(33L);

        doReturn(9L).when(executionResources).executeWithConnection(any(), any(), eq("count"));
        stubMappedExecutableQuery(query, new ExecutableQuery.CountQuery<>(selectBuilder));
        setField(query, "executionResources", executionResources);

        setField(query, "functionalInnerType", List.class);
        Object listResult = invoke(
                query,
                "adaptReturnTypeWriteFunctional",
                new Class[] {Object.class, Object[].class},
                List.of(user),
                new Object[0]);

        setField(query, "functionalInnerType", Stream.class);
        Object streamResult = invoke(
                query,
                "adaptReturnTypeWriteFunctional",
                new Class[] {Object.class, Object[].class},
                List.of(user),
                new Object[0]);

        setField(query, "functionalInnerType", Page.class);
        Object pageResult = invoke(
                query,
                "adaptReturnTypeWriteFunctional",
                new Class[] {Object.class, Object[].class},
                List.of(user),
                new Object[] {"name", pageable});

        setField(query, "functionalInnerType", Slice.class);
        Object sliceResult = invoke(
                query,
                "adaptReturnTypeWriteFunctional",
                new Class[] {Object.class, Object[].class},
                List.of(user),
                new Object[] {"name", pageable});

        assertThat(listResult)
                .isInstanceOf(Success.class)
                .extracting(r -> ((Success<List<User>>) r).value(), InstanceOfAssertFactories.LIST)
                .hasSize(1);
        assertThat(streamResult)
                .isInstanceOf(Success.class)
                .extracting(r -> ((Success<Stream<User>>) r).value())
                .isInstanceOf(Stream.class);
        assertThat(pageResult)
                .isInstanceOf(Success.class)
                .extracting(r -> ((Success<Page<User>>) r).value().getTotalElements())
                .isEqualTo(9L);
        assertThat(sliceResult)
                .isInstanceOf(Success.class)
                .extracting(r -> ((Success<Slice<User>>) r).value().getNumberOfElements())
                .isEqualTo(1);
    }

    @Test
    void adaptReturnTypeWriteFunctionalUnsupportedInnerTypeThrows() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(WriteProbeRepository.class, "deleteByEmail", String.class);
        setField(query, "functionalInnerType", String.class);

        List<User> users = List.of(new User("W", "w@test.com"));
        assertThatThrownBy(() -> invoke(
                        query,
                        "adaptReturnTypeWriteFunctional",
                        new Class[] {Object.class, Object[].class},
                        users,
                        new Object[0]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported WriteResult inner type");
    }

    @Test
    void adaptDeleteResultReadFunctionalSupportsLongIntegerBooleanAndRejectsUnsupported() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(ReadProbeRepository.class, "countByActive", Boolean.class);

        setField(query, "functionalInnerType", Long.class);
        Object longResult = invoke(query, "adaptDeleteResultReadFunctional", new Class[] {int.class}, 7);

        setField(query, "functionalInnerType", Integer.class);
        Object integerResult = invoke(query, "adaptDeleteResultReadFunctional", new Class[] {int.class}, 5);

        setField(query, "functionalInnerType", Boolean.class);
        Object boolResult = invoke(query, "adaptDeleteResultReadFunctional", new Class[] {int.class}, 0);

        setField(query, "functionalInnerType", String.class);
        assertThatThrownBy(() -> invoke(query, "adaptDeleteResultReadFunctional", new Class[] {int.class}, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported ReadResult inner type");

        assertThat(longResult).isEqualTo(7L);
        assertThat(integerResult).isEqualTo(5);
        assertThat(boolResult).isEqualTo(false);
    }

    @Test
    void adaptSelectResultSupportsPageAndSliceBranches() throws Exception {
        Pageable pageable = PageRequest.of(0, 2);
        User user = new User("P", "p@test.com").withId(44L);

        FluentRepositoryQuery<User, Long> pageQuery =
                queryFor(PageProbeRepository.class, "findByName", String.class, Pageable.class);
        QueryExecutionResources<User> pageExecutionResources = mock(QueryExecutionResources.class);
        doReturn(11L).when(pageExecutionResources).executeWithConnection(any(), any(), eq("count"));
        stubMappedExecutableQuery(pageQuery, new ExecutableQuery.CountQuery<>(mock(SelectBuilder.class)));
        setField(pageQuery, "executionResources", pageExecutionResources);

        Object pageResult = invoke(
                pageQuery, "adaptSelectResult", new Class[] {List.class, Object[].class}, List.of(user), new Object[] {
                    "name", pageable
                });

        FluentRepositoryQuery<User, Long> sliceQuery =
                queryFor(SliceProbeRepository.class, "findByName", String.class, Pageable.class);
        Object sliceResult = invoke(
                sliceQuery, "adaptSelectResult", new Class[] {List.class, Object[].class}, List.of(user), new Object[] {
                    "name", pageable
                });

        assertThat(pageResult)
                .isInstanceOf(Page.class)
                .extracting(p -> ((Page<?>) p).getTotalElements())
                .isEqualTo(11L);
        assertThat(sliceResult)
                .isInstanceOf(Slice.class)
                .extracting(s -> ((Slice<?>) s).getNumberOfElements())
                .isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void adaptSelectResultReadFunctionalSupportsPageSliceAndStreamBranches() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(ReadProbeRepository.class, "findByEmail", String.class);
        QueryExecutionResources<User> executionResources = mock(QueryExecutionResources.class);
        Pageable pageable = PageRequest.of(0, 2);
        User user = new User("R", "r@test.com").withId(55L);

        doReturn(8L).when(executionResources).executeWithConnection(any(), any(), eq("count"));
        stubMappedExecutableQuery(query, new ExecutableQuery.CountQuery<>(mock(SelectBuilder.class)));
        setField(query, "executionResources", executionResources);

        setField(query, "functionalInnerType", Page.class);
        Object pageResult = invoke(
                query,
                "adaptSelectResultReadFunctional",
                new Class[] {List.class, Object[].class},
                List.of(user),
                new Object[] {"name", pageable});

        setField(query, "functionalInnerType", Slice.class);
        Object sliceResult = invoke(
                query,
                "adaptSelectResultReadFunctional",
                new Class[] {List.class, Object[].class},
                List.of(user),
                new Object[] {"name", pageable});

        setField(query, "functionalInnerType", Stream.class);
        Object streamResult = invoke(
                query,
                "adaptSelectResultReadFunctional",
                new Class[] {List.class, Object[].class},
                List.of(user),
                new Object[0]);

        assertThat(pageResult)
                .isInstanceOf(Found.class)
                .extracting(r -> ((Found<Page<User>>) r).value().getTotalElements())
                .isEqualTo(8L);
        assertThat(sliceResult)
                .isInstanceOf(Found.class)
                .extracting(r -> ((Found<Slice<User>>) r).value().getNumberOfElements())
                .isEqualTo(1);
        assertThat(streamResult)
                .isInstanceOf(Found.class)
                .extracting(r -> ((Found<Stream<User>>) r).value())
                .isInstanceOf(Stream.class);
    }

    @Test
    void sortSupportsSortParameterAndNullFallback() throws Exception {
        FluentRepositoryQuery<User, Long> sortQuery =
                queryFor(StandardProbeRepository.class, "findByName", String.class, Sort.class);
        Sort sort = Sort.by(Sort.Order.asc("email"));

        Object sortResult =
                invoke(sortQuery, "sort", new Class[] {Object[].class}, (Object) new Object[] {"name", sort});
        assertThat(sortResult).isEqualTo(sort);

        FluentRepositoryQuery<User, Long> noSortQuery =
                queryFor(StandardProbeRepository.class, "findByEmail", String.class);
        Object nullSort =
                invoke(noSortQuery, "sort", new Class[] {Object[].class}, (Object) new Object[] {"mail@test.com"});
        assertThat(nullSort).isNull();
    }

    @Test
    void columnNameResolvesKnownPropertyAndFallsBackOnUnknown() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(StandardProbeRepository.class, "findByEmail", String.class);

        Object resolved = invoke(query, "columnName", new Class[] {String.class}, "placeOfResidence");
        Object fallback = invoke(query, "columnName", new Class[] {String.class}, "raw_column");

        assertThat(resolved).isEqualTo("address");
        assertThat(fallback).isEqualTo("raw_column");
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeNonFunctionalAdaptsRawListToEntity() throws Exception {
        User user = new User("Exec", "exec@test.com").withId(111L);
        FluentRepositoryQuery<User, Long> query = queryFor(StandardProbeRepository.class, "findByEmail", String.class);
        QueryExecutionResources<User> executionResources = mock(QueryExecutionResources.class);
        SelectBuilder selectBuilder = mock(SelectBuilder.class);

        doReturn(List.of(user)).when(executionResources).executeWithConnection(any(), any(), eq("find"));

        stubMappedExecutableQuery(query, new ExecutableQuery.EntitySelectQuery<>(selectBuilder));
        setField(query, "executionResources", executionResources);

        Object result = query.execute(new Object[] {"exec@test.com"});

        assertThat(result).isEqualTo(user);
    }

    @Test
    void executeReadFunctionalOnEmptyListReturnsNotFound() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(ReadProbeRepository.class, "findByEmail", String.class);
        QueryExecutionResources<User> executionResources = mock(QueryExecutionResources.class);
        SelectBuilder selectBuilder = mock(SelectBuilder.class);

        doReturn(List.of()).when(executionResources).executeWithConnection(any(), any(), eq("find"));

        stubMappedExecutableQuery(query, new ExecutableQuery.EntitySelectQuery<>(selectBuilder));
        setField(query, "executionResources", executionResources);

        Object result = query.execute(new Object[] {"missing@test.com"});

        assertThat(result).isInstanceOf(NotFound.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeWriteFunctionalDeleteReturnsSuccess() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(WriteProbeRepository.class, "deleteByEmail", String.class);
        QueryExecutionResources<User> executionResources = mock(QueryExecutionResources.class);
        DeleteBuilder deleteBuilder = mock(DeleteBuilder.class);

        doReturn(3).when(executionResources).executeWithConnection(any(), any(), eq("delete"));

        stubMappedExecutableQuery(query, new ExecutableQuery.DeleteQuery<>(deleteBuilder));
        setField(query, "executionResources", executionResources);

        Object result = query.execute(new Object[] {"delete@test.com"});

        assertThat(result)
                .isInstanceOf(Success.class)
                .extracting(r -> ((Success<Long>) r).value())
                .isEqualTo(3L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeReadFunctionalDataAccessExceptionReturnsError() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(ReadProbeRepository.class, "findByEmail", String.class);
        QueryExecutionResources<User> executionResources = mock(QueryExecutionResources.class);
        SelectBuilder selectBuilder = mock(SelectBuilder.class);

        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("boom"))
                .when(executionResources)
                .executeWithConnection(any(), any(), eq("find"));

        stubMappedExecutableQuery(query, new ExecutableQuery.EntitySelectQuery<>(selectBuilder));
        setField(query, "executionResources", executionResources);

        Object result = query.execute(new Object[] {"x"});

        assertThat(result)
                .isInstanceOf(Error.class)
                .extracting(r -> ((Error<User>) r).message())
                .asString()
                .contains("findByEmail");
    }

    @Test
    void sortAndOrderByClausesUsePageableSortAndColumnMappingFallback() throws Exception {
        FluentRepositoryQuery<User, Long> query =
                queryFor(StandardProbeRepository.class, "findByName", String.class, Pageable.class);
        Pageable pageable = PageRequest.of(1, 5, Sort.by(Sort.Order.desc("email")));

        Object sortResult =
                invoke(query, "sort", new Class[] {Object[].class}, (Object) new Object[] {"name", pageable});
        Object clauses = invoke(query, "orderByClauses", new Class[] {Sort.class}, sortResult);

        assertThat(sortResult).isInstanceOf(Sort.class);
        assertThat(clauses)
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .singleElement()
                .extracting(c -> ((OrderByClause) c).direction().name())
                .isEqualTo("DESC");
    }

    @Test
    void pageWindowAndExtractPageableUseRuntimePageable() throws Exception {
        FluentRepositoryQuery<User, Long> query =
                queryFor(StandardProbeRepository.class, "findByName", String.class, Pageable.class);
        Pageable pageable = PageRequest.of(2, 20);

        Object extracted = invoke(
                query, "extractPageable", new Class[] {Object[].class}, (Object) new Object[] {"name", pageable});
        Object pageWindow =
                invoke(query, "pageWindow", new Class[] {Object[].class}, (Object) new Object[] {"name", pageable});

        assertThat(extracted).isEqualTo(pageable);
        assertThat(pageWindow)
                .isInstanceOf(PageWindow.class)
                .extracting(w -> ((PageWindow) w).offset())
                .isEqualTo(40L);
    }

    @Test
    void queryRuntimeParamsCombinesSortAndPageWindow() throws Exception {
        FluentRepositoryQuery<User, Long> query =
                queryFor(StandardProbeRepository.class, "findByName", String.class, Pageable.class);
        Pageable pageable = PageRequest.of(3, 10, Sort.by("name"));

        Object params = invoke(
                query, "queryRuntimeParams", new Class[] {Object[].class}, (Object) new Object[] {"name", pageable});

        assertThat(params).isInstanceOf(QueryRuntimeParams.class);
        QueryRuntimeParams runtimeParams = (QueryRuntimeParams) params;
        assertThat(runtimeParams.runtimeSort()).hasSize(1);
        assertThat(runtimeParams.pageWindow()).isNotNull();
    }

    @Test
    void adaptAsPageUsesCountQueryForTotal() throws Exception {
        FluentRepositoryQuery<User, Long> query =
                queryFor(PageProbeRepository.class, "findByName", String.class, Pageable.class);
        Pageable pageable = PageRequest.of(1, 2);
        User user = new User("Page", "page@test.com").withId(1L);
        QueryExecutionResources<User> executionResources = mock(QueryExecutionResources.class);
        SelectBuilder selectBuilder = mock(SelectBuilder.class);

        doReturn(7L).when(executionResources).executeWithConnection(any(), any(), eq("count"));

        stubMappedExecutableQuery(query, new ExecutableQuery.CountQuery<>(selectBuilder));
        setField(query, "executionResources", executionResources);

        Object page =
                invoke(query, "adaptAsPage", new Class[] {List.class, Object[].class}, List.of(user), new Object[] {
                    "name", pageable
                });

        assertThat(page)
                .isInstanceOf(Page.class)
                .extracting(p -> ((Page<?>) p).getTotalElements())
                .isEqualTo(7L);
    }

    @Test
    void adaptAsSliceBuildsSliceWithProvidedPageable() throws Exception {
        FluentRepositoryQuery<User, Long> query =
                queryFor(SliceProbeRepository.class, "findByName", String.class, Pageable.class);
        Pageable pageable = PageRequest.of(0, 2);
        User user = new User("Slice", "slice@test.com").withId(2L);

        Object slice =
                invoke(query, "adaptAsSlice", new Class[] {List.class, Object[].class}, List.of(user), new Object[] {
                    "name", pageable
                });

        assertThat(slice)
                .isInstanceOf(Slice.class)
                .extracting(s -> ((Slice<?>) s).getNumberOfElements())
                .isEqualTo(1);
    }

    private FluentRepositoryQuery<User, Long> queryFor(
            Class<?> repositoryInterface, String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = repositoryInterface.getMethod(methodName, parameterTypes);
        RepositoryMetadata metadata = new DefaultRepositoryMetadata(repositoryInterface);
        SpelAwareProxyProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
        FluentEntityInformation<User, Long> entityInformation = new FluentEntityInformation<>(User.class);
        FluentConnectionProvider connectionProvider = mock(FluentConnectionProvider.class);
        DSL dsl = mock(DSL.class);

        return new FluentRepositoryQuery<>(
                method, metadata, projectionFactory, entityInformation, connectionProvider, dsl);
    }

    @SuppressWarnings("unchecked")
    private <R> R invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return (R) method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void stubMappedExecutableQuery(
            FluentRepositoryQuery<User, Long> query, ExecutableQuery<User> executableQuery) throws Exception {
        Field dslMapperField = query.getClass().getDeclaredField("dslMapper");
        dslMapperField.setAccessible(true);
        Object mapper = dslMapperField.get(query);

        Field buildStrategiesField = mapper.getClass().getDeclaredField("buildStrategies");
        buildStrategiesField.setAccessible(true);
        Function<Object, Object> buildStrategies =
                ignored -> (MappedQueryStrategy<User, Long>) (descriptor, args, runtimeParams) -> executableQuery;
        buildStrategiesField.set(mapper, buildStrategies);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
