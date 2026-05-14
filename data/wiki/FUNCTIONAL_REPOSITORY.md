# Functional Repository (Split Read/Write API)

## Breaking change in v1.4.0

Starting from v1.4.0, functional repositories are split into dedicated read and write contracts.

- Read contract returns `ReadResult<T>` with three explicit states:
  - `Found<T>`
  - `NotFound<T>`
  - `Failure<T>`
- Write contract returns `WriteResult<T>` with two states:
  - `Success<T>`
  - `Failure<T>`

Legacy `RepositoryResult<T>`, `FunctionalCrudRepository`, and `FunctionalPagingAndSortingRepository` are no longer part of the functional public API.

## Why this change

The old model represented not-found reads as `Success(Optional.empty())`, which made signatures and calling code verbose.

The new model makes read outcomes explicit without `Optional` wrappers for single-item queries.

## New interfaces

```java
public interface FunctionalReadRepository<T, ID> extends Repository<T, ID> {
    ReadResult<T> findById(ID id);
    ReadResult<Boolean> existsById(ID id);
    ReadResult<List<T>> findAll();
    ReadResult<List<T>> findAllById(Iterable<ID> ids);
    ReadResult<Long> count();
}

public interface FunctionalReadPagingAndSortingRepository<T, ID> extends Repository<T, ID> {
    ReadResult<List<T>> findAll(Sort sort);
    ReadResult<Page<T>> findAll(Pageable pageable);
}

public interface FunctionalWriteRepository<T, ID> extends Repository<T, ID> {
    <S extends T> WriteResult<S> save(S entity);
    <S extends T> WriteResult<List<S>> saveAll(Iterable<S> entities);
    WriteResult<Boolean> deleteById(ID id);
    WriteResult<Boolean> delete(T entity);
    WriteResult<Long> deleteAllById(Iterable<? extends ID> ids);
    WriteResult<Long> deleteAll(Iterable<? extends T> entities);
    WriteResult<Long> deleteAll();
}
```

## Derived query return type rules

For repository methods derived from method names (`findBy...`, `countBy...`, `existsBy...`, `deleteBy...`):

- `findBy...` on read repositories:
  - Single result type: `ReadResult<Entity>` (`NotFound` when empty)
  - Multi result type: `ReadResult<List<Entity>>` (empty list is `Found(empty)`)
- `countBy...` on read repositories:
  - `ReadResult<Long>`
- `existsBy...` on read repositories:
  - `ReadResult<Boolean>`
- `deleteBy...` on write repositories:
  - `WriteResult<Long>`, `WriteResult<Integer>`, or `WriteResult<Boolean>`

## Error model

- Read APIs map infrastructure errors (`DataAccessException`) to `ReadResult.Failure`.
- Write APIs keep explicit domain-level failures in `WriteResult.Failure`.

## Before/After migration

Before (v1.3.x):

```java
RepositoryResult<Optional<User>> result = repository.findByEmail(email);
result.fold(
    value -> value.ifPresent(this::handleFound),
    failure -> handleFailure(failure.message())
);
```

After (v1.4.0+):

```java
ReadResult<User> result = repository.findByEmail(email);
result.fold(
    this::handleFound,
    this::handleNotFound,
    failure -> handleFailure(failure.message())
);
```

Before (write):

```java
RepositoryResult<Boolean> deleted = repository.deleteById(id);
```

After (write):

```java
WriteResult<Boolean> deleted = repository.deleteById(id);
```

## Example repository

```java
public interface UserRepository
        extends FunctionalReadRepository<User, Long>,
                FunctionalReadPagingAndSortingRepository<User, Long>,
                FunctionalWriteRepository<User, Long> {

    ReadResult<User> findByEmail(String email);
    ReadResult<List<User>> findByName(String name);
    ReadResult<Long> countByActive(Boolean active);
    ReadResult<Boolean> existsByEmail(String email);
    WriteResult<Long> deleteByEmail(String email);
}
```

