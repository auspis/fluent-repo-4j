package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.auspis.fluentrepo4j.FluentPersistable;
import io.github.auspis.fluentrepo4j.functional.RepositoryResult;
import io.github.auspis.fluentrepo4j.functional.RepositoryResult.Failure;
import io.github.auspis.fluentrepo4j.functional.RepositoryResult.Success;
import io.github.auspis.fluentrepo4j.test.domain.User;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * Unit tests for {@link FunctionalFluentRepository} covering branches and edge cases
 * not exercised by the Spring-wired integration tests.
 */
@SuppressWarnings("unchecked")
class FunctionalFluentRepositoryTest {

    private CoreRepositoryOperations<User, Long> core;
    private SaveDecisionResolver<User, Long> resolver;
    private FunctionalFluentRepository<User, Long> repository;

    @BeforeEach
    void setUp() {
        core = mock(CoreRepositoryOperations.class);
        resolver = mock(SaveDecisionResolver.class);
        when(core.getSaveDecisionResolver()).thenReturn(resolver);
        repository = new FunctionalFluentRepository<>(core);
    }

    @Nested
    class SaveBranches {

        @Test
        void save_insertAutoId() {
            User user = new User("Alice", "alice@test.com");
            when(resolver.apply(user)).thenReturn(SaveAction.INSERT_AUTO_ID);
            when(core.insertWithIdentity(user)).thenReturn(user);

            RepositoryResult<User> result = repository.save(user);

            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isSameAs(user);
            verify(core).insertWithIdentity(user);
        }

        @Test
        void save_update() {
            User user = new User("Alice", "alice@test.com").withId(1L);
            when(resolver.apply(user)).thenReturn(SaveAction.UPDATE);
            when(core.update(user)).thenReturn(user);

            RepositoryResult<User> result = repository.save(user);

            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isSameAs(user);
            verify(core).update(user);
        }

        @Test
        void save_errorReturnsFailure() {
            User user = new User("Alice", "alice@test.com").withId(99L);
            when(resolver.apply(user)).thenReturn(SaveAction.ERROR);

            RepositoryResult<User> result = repository.save(user);

            assertThat(result).isInstanceOf(Failure.class);
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void save_optimisticLockingFailureReturnsFailure() {
            User user = new User("Alice", "alice@test.com").withId(1L);
            when(resolver.apply(user)).thenReturn(SaveAction.UPDATE);
            when(core.update(user)).thenThrow(new OptimisticLockingFailureException("Entity not found for update"));

            RepositoryResult<User> result = repository.save(user);

            assertThat(result).isInstanceOf(Failure.class);
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void save_fluentPersistableMarkedPersisted() {
            FluentPersistable<Integer> entity = mock(FluentPersistable.class);

            CoreRepositoryOperations<FluentPersistable<Integer>, Integer> fpCore = mock(CoreRepositoryOperations.class);
            SaveDecisionResolver<FluentPersistable<Integer>, Integer> fpResolver = mock(SaveDecisionResolver.class);
            when(fpCore.getSaveDecisionResolver()).thenReturn(fpResolver);
            when(fpResolver.apply(entity)).thenReturn(SaveAction.INSERT_PROVIDED_ID);
            when(fpCore.insertWithProvidedId(entity)).thenReturn(entity);

            FunctionalFluentRepository<FluentPersistable<Integer>, Integer> fpRepo =
                    new FunctionalFluentRepository<>(fpCore);

            RepositoryResult<FluentPersistable<Integer>> result = fpRepo.save(entity);

            assertThat(result).isInstanceOf(Success.class);
            verify(entity).markPersisted();
        }
    }

    @Nested
    class SaveAllBranches {

        @Test
        void saveAll_stopsOnFirstFailure() {
            User good = new User("Good", "good@test.com").withId(1L);
            User bad = new User("Bad", "bad@test.com").withId(99L);

            when(resolver.apply(good)).thenReturn(SaveAction.INSERT_PROVIDED_ID);
            when(core.insertWithProvidedId(good)).thenReturn(good);
            when(resolver.apply(bad)).thenReturn(SaveAction.ERROR);

            RepositoryResult<List<User>> result = repository.saveAll(List.of(good, bad));

            assertThat(result).isInstanceOf(Failure.class);
        }
    }

    @Nested
    class FindAllPagedBranches {

        @Test
        void findAllPaged_emptyWhenOffsetExceedsTotal() {
            when(core.countRaw()).thenReturn(5L);

            RepositoryResult<Page<User>> result = repository.findAll(PageRequest.of(10, 3));

            assertThat(result).isInstanceOf(Success.class);
            Page<User> page = result.orElseThrow();
            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(5);
        }

        @Test
        void findAllPaged_emptyWhenZeroTotal() {
            when(core.countRaw()).thenReturn(0L);

            RepositoryResult<Page<User>> result = repository.findAll(PageRequest.of(0, 10));

            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow().getContent()).isEmpty();
            assertThat(result.orElseThrow().getTotalElements()).isZero();
        }
    }

    @Nested
    class DeleteBranches {

        @Test
        void delete_nullIdReturnsFailure() {
            User user = new User("NoId", "noid@test.com");
            when(core.getEntityId(user)).thenReturn(null);

            RepositoryResult<Boolean> result = repository.delete(user);

            assertThat(result).isInstanceOf(Failure.class);
        }

        @Test
        void deleteAllEntities_countsDeleted() {
            User u1 = new User("A", "a@test.com").withId(1L);
            User u2 = new User("B", "b@test.com").withId(2L);
            when(core.getEntityId(u1)).thenReturn(1L);
            when(core.getEntityId(u2)).thenReturn(2L);
            when(core.deleteByIdRaw(1L)).thenReturn(1);
            when(core.deleteByIdRaw(2L)).thenReturn(0);

            RepositoryResult<Long> result = repository.deleteAll(List.of(u1, u2));

            assertThat(result).isInstanceOf(Success.class);
            assertThat(result.orElseThrow()).isEqualTo(1L);
        }
    }
}
