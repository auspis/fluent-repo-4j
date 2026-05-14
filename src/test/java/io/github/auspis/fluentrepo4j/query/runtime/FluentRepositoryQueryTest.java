package io.github.auspis.fluentrepo4j.query.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.functional.read.FunctionalReadRepository;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Found;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.NotFound;
import io.github.auspis.fluentrepo4j.functional.write.FunctionalWriteRepository;
import io.github.auspis.fluentrepo4j.functional.write.WriteResult;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;

class FluentRepositoryQueryTest {

    interface ReadProbeRepository extends FunctionalReadRepository<User, Long> {
        ReadResult<User> findByEmail(String email);

        ReadResult<List<User>> findByName(String name);

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
    void adaptReadSingleResultReturnsNotFoundForEmptyResults() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(ReadProbeRepository.class, "findByEmail", String.class);

        Object result = invokePrivate(
                query,
                "adaptSelectResultReadFunctional",
                new Class[] {List.class, Object[].class},
                List.of(),
                new Object[0]);

        assertThat(result).isInstanceOf(NotFound.class);
    }

    @Test
    void adaptReadSingleResultReturnsFoundForFirstResult() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(ReadProbeRepository.class, "findByEmail", String.class);
        User user = new User("User", "u@test.com").withId(10L);

        Object result = invokePrivate(
                query,
                "adaptSelectResultReadFunctional",
                new Class[] {List.class, Object[].class},
                List.of(user),
                new Object[0]);

        assertThat(result).isInstanceOf(Found.class);
        assertThat(((Found<User>) result).value()).isEqualTo(user);
    }

    @Test
    void adaptReadListResultReturnsFoundList() throws Exception {
        FluentRepositoryQuery<User, Long> query = queryFor(ReadProbeRepository.class, "findByName", String.class);
        User user = new User("List", "list@test.com").withId(11L);

        Object result = invokePrivate(
                query,
                "adaptSelectResultReadFunctional",
                new Class[] {List.class, Object[].class},
                List.of(user),
                new Object[0]);

        assertThat(result).isInstanceOf(Found.class);
        assertThat(((Found<List<User>>) result).value()).hasSize(1);
    }

    @Test
    void adaptWriteDeleteResultSupportsLongAndBoolean() throws Exception {
        FluentRepositoryQuery<User, Long> longDelete =
                queryFor(WriteProbeRepository.class, "deleteByEmail", String.class);
        FluentRepositoryQuery<User, Long> boolDelete =
                queryFor(WriteProbeRepository.class, "deleteByName", String.class);

        Object longResult = invokePrivate(longDelete, "adaptDeleteResultWriteFunctional", new Class[] {int.class}, 3);
        Object boolResult = invokePrivate(boolDelete, "adaptDeleteResultWriteFunctional", new Class[] {int.class}, 0);

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
    private <R> R invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
            throws Exception {
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
