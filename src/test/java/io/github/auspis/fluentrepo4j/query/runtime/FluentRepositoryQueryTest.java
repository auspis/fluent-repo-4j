package io.github.auspis.fluentrepo4j.query.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.functional.FunctionalCrudRepository;
import io.github.auspis.fluentrepo4j.functional.read.FunctionalReadRepository;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Found;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.NotFound;
import io.github.auspis.fluentrepo4j.functional.write.FunctionalWriteRepository;
import io.github.auspis.fluentrepo4j.functional.write.WriteResult;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.query.runtime.FluentRepositoryQueryTest.FoundReadFunctionalCase.ListEntityFoundReadFunctionalCase;
import io.github.auspis.fluentrepo4j.query.runtime.FluentRepositoryQueryTest.FoundReadFunctionalCase.SingleEntityFoundReadFunctionalCase;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
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
}
