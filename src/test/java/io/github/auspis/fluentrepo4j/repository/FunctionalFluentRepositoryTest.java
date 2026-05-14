package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.auspis.fluentrepo4j.FluentPersistable;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Error;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Found;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.NotFound;
import io.github.auspis.fluentrepo4j.functional.write.WriteResult;
import io.github.auspis.fluentrepo4j.functional.write.WriteResult.Success;
import io.github.auspis.fluentrepo4j.test.domain.User;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@SuppressWarnings("unchecked")
class FunctionalFluentRepositoryTest {

    private CoreRepositoryOperations<User, Long> core;
    private SaveDecisionResolver<User, Long> resolver;
    private FunctionalReadFluentRepository<User, Long> readRepository;
    private FunctionalWriteFluentRepository<User, Long> writeRepository;

    @BeforeEach
    void setUp() {
        core = mock(CoreRepositoryOperations.class);
        resolver = mock(SaveDecisionResolver.class);
        when(core.getSaveDecisionResolver()).thenReturn(resolver);
        readRepository = new FunctionalReadFluentRepository<>(core);
        writeRepository = new FunctionalWriteFluentRepository<>(core);
    }

    @Nested
    class WriteBranches {

        @Test
        void save_insertAutoId() {
            User user = new User("Alice", "alice@test.com");
            when(resolver.apply(user)).thenReturn(SaveAction.INSERT_AUTO_ID);
            when(core.insertWithIdentity(user)).thenReturn(user);

            WriteResult<User> result = writeRepository.save(user);

            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isSameAs(user);
            verify(core).insertWithIdentity(user);
        }

        @Test
        void save_errorReturnsFailure() {
            User user = new User("Alice", "alice@test.com").withId(99L);
            when(resolver.apply(user)).thenReturn(SaveAction.ERROR);

            WriteResult<User> result = writeRepository.save(user);

            assertThat(result.isError()).isTrue();
        }

        @Test
        void save_optimisticLockingFailureReturnsFailure() {
            User user = new User("Alice", "alice@test.com").withId(1L);
            when(resolver.apply(user)).thenReturn(SaveAction.UPDATE);
            when(core.update(user)).thenThrow(new OptimisticLockingFailureException("Entity not found for update"));

            WriteResult<User> result = writeRepository.save(user);

            assertThat(result.isError()).isTrue();
        }

        @Test
        void save_fluentPersistableMarkedPersisted() {
            FluentPersistable<Integer> entity = mock(FluentPersistable.class);

            CoreRepositoryOperations<FluentPersistable<Integer>, Integer> fpCore = mock(CoreRepositoryOperations.class);
            SaveDecisionResolver<FluentPersistable<Integer>, Integer> fpResolver = mock(SaveDecisionResolver.class);
            when(fpCore.getSaveDecisionResolver()).thenReturn(fpResolver);
            when(fpResolver.apply(entity)).thenReturn(SaveAction.INSERT_PROVIDED_ID);
            when(fpCore.insertWithProvidedId(entity)).thenReturn(entity);

            FunctionalWriteFluentRepository<FluentPersistable<Integer>, Integer> fpRepo =
                    new FunctionalWriteFluentRepository<>(fpCore);

            WriteResult<FluentPersistable<Integer>> result = fpRepo.save(entity);

            assertThat(result.isSuccess()).isTrue();
            verify(entity).markPersisted();
        }

        @Test
        void deleteAllEntities_countsDeleted() {
            User u1 = new User("A", "a@test.com").withId(1L);
            User u2 = new User("B", "b@test.com").withId(2L);
            when(core.getEntityId(u1)).thenReturn(1L);
            when(core.getEntityId(u2)).thenReturn(2L);
            when(core.deleteByIdRaw(1L)).thenReturn(1);
            when(core.deleteByIdRaw(2L)).thenReturn(0);

            WriteResult<Long> result = writeRepository.deleteAll(List.of(u1, u2));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.orElseThrow()).isEqualTo(1L);
        }
    }

    @Nested
    class ReadBranches {

        @Test
        void findById_returnsFound() {
            User user = new User("Read", "read@test.com").withId(1L);
            when(core.findByIdRaw(1L)).thenReturn(Optional.of(user));

            ReadResult<User> result = readRepository.findById(1L);

            assertThat(result).isInstanceOf(Found.class);
            assertThat(result.orElseThrow()).isEqualTo(user);
        }

        @Test
        void findById_returnsNotFound() {
            when(core.findByIdRaw(999L)).thenReturn(Optional.empty());

            ReadResult<User> result = readRepository.findById(999L);

            assertThat(result).isInstanceOf(NotFound.class);
        }

        @Test
        void findAllPaged_emptyWhenOffsetExceedsTotal() {
            when(core.countRaw()).thenReturn(5L);

            ReadResult<Page<User>> result = readRepository.findAll(PageRequest.of(10, 3));

            assertThat(result).isInstanceOf(Found.class);
            Page<User> page = result.orElseThrow();
            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(5);
        }

        @Test
        void findAllPaged_emptyWhenZeroTotal() {
            when(core.countRaw()).thenReturn(0L);

            ReadResult<Page<User>> result = readRepository.findAll(PageRequest.of(0, 10));

            assertThat(result).isInstanceOf(Found.class);
            assertThat(result.orElseThrow().getContent()).isEmpty();
            assertThat(result.orElseThrow().getTotalElements()).isZero();
        }

        @Test
        void readDataAccessException_returnsFailure() {
            when(core.findAllRaw(org.springframework.data.domain.Sort.unsorted(), null))
                    .thenThrow(new TransientDataAccessResourceException("db down"));

            ReadResult<List<User>> result = readRepository.findAll();

            assertThat(result).isInstanceOf(Error.class);
            assertThat(result.isError()).isTrue();
        }
    }
}
