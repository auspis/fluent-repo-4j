# Functional Repository (Split Results + Spring-Aligned Interfaces)

## Breaking change in v1.4.0

From v1.4.0 onward, functional repositories use split read/write result wrappers:

- Read operations return `ReadResult<T>`:
  - `Found<T>`
  - `NotFound<T>`
  - `Error<T>`
- Write operations return `WriteResult<T>`:
  - `Success<T>`
  - `Error<T>`

`RepositoryResult<T>` and the old monolithic functional API are removed.

## High-level interfaces (recommended)

The API is aligned with Spring Data conventions where CRUD and paging are separate concerns.

```java
public interface FunctionalCrudRepository<T, ID>
        extends FunctionalReadRepository<T, ID>, FunctionalWriteRepository<T, ID> {
}

public interface FunctionalPagingAndSortingRepository<T, ID> extends Repository<T, ID> {
    ReadResult<List<T>> findAll(Sort sort);
    ReadResult<Page<T>> findAll(Pageable pageable);
}
```

Recommended composition:

- CRUD only: `FunctionalCrudRepository<T, ID>`
- CRUD + paging/sorting: `FunctionalCrudRepository<T, ID>` + `FunctionalPagingAndSortingRepository<T, ID>`
- Advanced split usage: `FunctionalReadRepository<T, ID>` and/or `FunctionalWriteRepository<T, ID>` directly

## Low-level split interfaces (advanced)

```java
public interface FunctionalReadRepository<T, ID> extends Repository<T, ID> {
    ReadResult<T> findById(ID id);
    ReadResult<Boolean> existsById(ID id);
    ReadResult<List<T>> findAll();
    ReadResult<List<T>> findAllById(Iterable<ID> ids);
    ReadResult<Long> count();
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

For method-name-derived queries (`findBy...`, `countBy...`, `existsBy...`, `deleteBy...`):

- `findBy...` on read contracts:
  - Single result: `ReadResult<Entity>` (`NotFound` when empty)
  - Multi result: `ReadResult<List<Entity>>` (`NotFound` when no rows match — use the `NotFound` branch to handle the empty case)
- `countBy...` on read contracts: `ReadResult<Long>`
- `existsBy...` on read contracts: `ReadResult<Boolean>`
- `deleteBy...` on write contracts: `WriteResult<Long>`, `WriteResult<Integer>`, or `WriteResult<Boolean>`

## Error model

- Read-side infrastructure errors (`DataAccessException`) are mapped to `ReadResult.Error`.
- Write-side infrastructure/domain errors are mapped to `WriteResult.Error`.

## Migration snapshot

Before (v1.3.x):

```java
RepositoryResult<Optional<User>> result = repository.findByEmail(email);
```

After (v1.4.0+):

```java
ReadResult<User> result = repository.findByEmail(email);

result.fold(
        this::handleFound,
        this::handleNotFound,
        error -> handleError(error.message())
);
```

## Example repository

```java
public interface UserRepository
        extends FunctionalCrudRepository<User, Long>,
                FunctionalPagingAndSortingRepository<User, Long> {

    ReadResult<User> findByEmail(String email);
    ReadResult<List<User>> findByName(String name);
    ReadResult<Long> countByActive(Boolean active);
    ReadResult<Boolean> existsByEmail(String email);
    WriteResult<Long> deleteByEmail(String email);
}
```

