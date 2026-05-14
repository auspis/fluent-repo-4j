package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.auspis.fluentrepo4j.functional.read.ReadResult;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Found;
import io.github.auspis.fluentrepo4j.test.domain.User;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryFacadeCoreInjectionTest {

    @Test
    void fluentRepository_canBeConstructedWithInjectedCore() {
        CoreRepositoryOperations<User, Long> core = mock(CoreRepositoryOperations.class);
        FluentRepository<User, Long> repository = new FluentRepository<>(core);
        User user = new User("A", "a@test.com").withId(1L);

        when(core.findByIdRaw(1L)).thenReturn(Optional.of(user));

        Optional<User> found = repository.findById(1L);

        assertThat(found).contains(user);
        verify(core).findByIdRaw(1L);
    }

    @Test
    void functionalReadRepository_canBeConstructedWithInjectedCore() {
        CoreRepositoryOperations<User, Long> core = mock(CoreRepositoryOperations.class);
        FunctionalReadFluentRepository<User, Long> repository = new FunctionalReadFluentRepository<>(core);
        User user = new User("B", "b@test.com").withId(2L);

        when(core.findByIdRaw(2L)).thenReturn(Optional.of(user));

        ReadResult<User> found = repository.findById(2L);

        assertThat(found).isInstanceOf(Found.class);
        assertThat(found.orElseThrow()).isEqualTo(user);
        verify(core).findByIdRaw(2L);
    }
}
