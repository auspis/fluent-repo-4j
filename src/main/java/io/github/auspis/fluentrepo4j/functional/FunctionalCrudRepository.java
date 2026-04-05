package io.github.auspis.fluentrepo4j.functional;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Functional counterpart of Spring Data's {@link org.springframework.data.repository.CrudRepository}.
 *
 * <p>Every operation returns a {@link RepositoryResult} instead of throwing exceptions for
 * domain-level failures (e.g. optimistic locking conflicts, validation errors). Infrastructure
 * errors such as connection failures continue to propagate as Spring's {@code DataAccessException}.
 *
 * <p>This interface is <b>mutually exclusive</b> with {@code CrudRepository}: a repository
 * interface must extend one or the other, not both, because several methods share the same
 * name and parameter list but differ in return type.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * public interface UserRepository
 *         extends FunctionalCrudRepository<User, Long>,
 *                 FunctionalPagingAndSortingRepository<User, Long> {
 *
 *     RepositoryResult<Optional<User>> findByEmail(String email);
 * }
 * }</pre>
 *
 * @param <T>  the entity type
 * @param <ID> the entity identifier type
 * @see RepositoryResult
 */
@NoRepositoryBean
public interface FunctionalCrudRepository<T, ID> extends Repository<T, ID> {

    // ---- Save ----

    /**
     * Saves the given entity.
     *
     * <p>Returns {@link RepositoryResult.Success} with the saved entity on success,
     * or {@link RepositoryResult.Failure} when the entity cannot be persisted
     * (e.g. optimistic locking conflict, inconsistent ID state).
     *
     * @param entity the entity to save, must not be {@literal null}
     * @return a {@link RepositoryResult} carrying the saved entity or a failure description
     */
    <S extends T> RepositoryResult<S> save(S entity);

    /**
     * Saves all given entities.
     *
     * <p>Returns {@link RepositoryResult.Success} with the list of saved entities if
     * <b>all</b> saves succeed, or {@link RepositoryResult.Failure} on the first failure.
     *
     * @param entities must not be {@literal null} nor contain {@literal null}
     * @return a {@link RepositoryResult} carrying the saved entities or a failure description
     */
    <S extends T> RepositoryResult<List<S>> saveAll(Iterable<S> entities);

    // ---- Find ----

    /**
     * Retrieves an entity by its id.
     *
     * <p>Returns {@link RepositoryResult.Success} with {@link Optional#empty()} when the
     * entity is not found — absence is <b>not</b> a failure.
     *
     * @param id must not be {@literal null}
     * @return a {@link RepositoryResult} carrying the optional entity
     */
    RepositoryResult<Optional<T>> findById(ID id);

    /**
     * Returns whether an entity with the given id exists.
     *
     * @param id must not be {@literal null}
     * @return a {@link RepositoryResult} carrying {@code true} if the entity exists
     */
    RepositoryResult<Boolean> existsById(ID id);

    /**
     * Returns all instances of the type.
     *
     * @return a {@link RepositoryResult} carrying all entities
     */
    RepositoryResult<List<T>> findAll();

    /**
     * Returns all instances with the given IDs.
     *
     * <p>If some IDs are not found, no entities are returned for those IDs.
     *
     * @param ids must not be {@literal null} nor contain {@literal null}
     * @return a {@link RepositoryResult} carrying the found entities
     */
    RepositoryResult<List<T>> findAllById(Iterable<ID> ids);

    /**
     * Returns the number of entities available.
     *
     * @return a {@link RepositoryResult} carrying the entity count
     */
    RepositoryResult<Long> count();

    // ---- Delete ----

    /**
     * Deletes the entity with the given id.
     *
     * <p>Returns {@link RepositoryResult.Success} with {@code true} if the entity was deleted,
     * or {@code false} if no entity with that id existed.
     *
     * @param id must not be {@literal null}
     * @return a {@link RepositoryResult} carrying the deletion outcome
     */
    RepositoryResult<Boolean> deleteById(ID id);

    /**
     * Deletes the given entity.
     *
     * <p>Returns {@link RepositoryResult.Success} with {@code true} if the entity was deleted,
     * or {@code false} if it was not found.
     *
     * @param entity must not be {@literal null}
     * @return a {@link RepositoryResult} carrying the deletion outcome
     */
    RepositoryResult<Boolean> delete(T entity);

    /**
     * Deletes all entities with the given IDs.
     *
     * <p>Returns {@link RepositoryResult.Success} with the number of entities actually deleted.
     *
     * @param ids must not be {@literal null}, must not contain {@literal null}
     * @return a {@link RepositoryResult} carrying the count of deleted entities
     */
    RepositoryResult<Long> deleteAllById(Iterable<? extends ID> ids);

    /**
     * Deletes all given entities.
     *
     * <p>Returns {@link RepositoryResult.Success} with the number of entities actually deleted.
     *
     * @param entities must not be {@literal null}, must not contain {@literal null}
     * @return a {@link RepositoryResult} carrying the count of deleted entities
     */
    RepositoryResult<Long> deleteAll(Iterable<? extends T> entities);

    /**
     * Deletes all entities managed by the repository.
     *
     * <p>Returns {@link RepositoryResult.Success} with the number of entities deleted.
     *
     * @return a {@link RepositoryResult} carrying the count of deleted entities
     */
    RepositoryResult<Long> deleteAll();
}
