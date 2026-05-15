# Release Notes - v1.4.0

Release date: 2026-05-12

## Highlights

Functional repository API has been redesigned with an intentional **BREAKING** change:

- Read operations now use `ReadResult<T>` with explicit states: `Found`, `NotFound`, `Error`.
- Multi-result read methods (`List`, `Page`, `Slice`, `Stream`) return `NotFound` when no rows match, consistent with single-result behavior.
- Write operations now use `WriteResult<T>` with states: `Success`, `Error`.
- High-level functional interfaces are available and aligned with Spring Data naming:
  - `FunctionalCrudRepository<T, ID>`
  - `FunctionalPagingAndSortingRepository<T, ID>`
- Low-level split contracts remain available:
  - `FunctionalReadRepository<T, ID>`
  - `FunctionalWriteRepository<T, ID>`

## BREAKING CHANGES

- Removed functional API types:
  - `io.github.auspis.fluentrepo4j.functional.RepositoryResult`
- Removed monolithic functional implementation class:
  - `io.github.auspis.fluentrepo4j.repository.FunctionalFluentRepository`

## Migration Guide

### 1) Repository interfaces

Before (v1.3.x):

```java
public interface UserRepository
        extends FunctionalCrudRepository<User, Long>,
                FunctionalPagingAndSortingRepository<User, Long> {

    RepositoryResult<Optional<User>> findByEmail(String email);
    RepositoryResult<List<User>> findByName(String name);
}
```

After (v1.4.0+):

```java
public interface UserRepository
  extends FunctionalCrudRepository<User, Long>,
    FunctionalPagingAndSortingRepository<User, Long> {

    ReadResult<User> findByEmail(String email);
    ReadResult<List<User>> findByName(String name);
}
```

### 2) Single-item read semantics

Before (v1.3.x):

```java
RepositoryResult<Optional<User>> result = repository.findByEmail(email);
```

After (v1.4.0+):

```java
ReadResult<User> result = repository.findByEmail(email);
```

Result handling now distinguishes explicitly:

- `Found<User>`
- `NotFound<User>`
- `Error<User>`

### 3) Write semantics

Before (v1.3.x):

```java
RepositoryResult<Boolean> deleted = repository.deleteById(id);
```

After (v1.4.0+):

```java
WriteResult<Boolean> deleted = repository.deleteById(id);
```

## Additional Notes

- Derived query support remains available for functional repositories with new wrapper mapping rules.
- Read-side infrastructure failures are surfaced as `ReadResult.Error`.
- Write-side failures are surfaced as `WriteResult.Error`.

## Full Changelog

- https://github.com/auspis/fluent-repo-4j/compare/v1.3.0...v1.4.0

