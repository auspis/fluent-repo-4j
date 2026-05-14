# Release Notes - v1.4.0

Release date: 2026-05-12

## Highlights

Functional repository API has been redesigned with an intentional **BREAKING** change:

- Read operations now use `ReadResult<T>` with explicit states: `Found`, `NotFound`, `Failure`.
- Write operations now use `WriteResult<T>` with states: `Success`, `Failure`.
- Legacy functional API based on `RepositoryResult<T>`, `FunctionalCrudRepository`, and `FunctionalPagingAndSortingRepository` has been removed from the public functional path.

## BREAKING CHANGES

- Removed functional API types:
  - `io.github.auspis.fluentrepo4j.functional.RepositoryResult`
  - `io.github.auspis.fluentrepo4j.functional.FunctionalCrudRepository`
  - `io.github.auspis.fluentrepo4j.functional.FunctionalPagingAndSortingRepository`
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
        extends FunctionalReadRepository<User, Long>,
                FunctionalReadPagingAndSortingRepository<User, Long>,
                FunctionalWriteRepository<User, Long> {

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
- `Failure<User>`

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
- Read-side infrastructure failures are surfaced as `ReadResult.Failure`.

## Full Changelog

- https://github.com/auspis/fluent-repo-4j/compare/v1.3.0...v1.4.0

