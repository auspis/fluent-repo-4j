# Functional Repository

A core differentiating feature of fluent-repo-4j: **repository interfaces that return
`RepositoryResult<T>` instead of throwing exceptions for domain-level failures**.

## Table of Contents

1. [Motivation](#motivation)
2. [The Problem with Spring Data's Default Interfaces](#the-problem-with-spring-datas-default-interfaces)
3. [How Functional Repositories Solve This](#how-functional-repositories-solve-this)
4. [RepositoryResult API](#repositoryresult-api)
5. [Functional Interfaces](#functional-interfaces)
6. [Derived Query Methods](#derived-query-methods)
7. [Error Model](#error-model)
8. [Usage Examples](#usage-examples)
9. [Mutual Exclusivity](#mutual-exclusivity)
10. [Comparison Table](#comparison-table)

---

## Motivation

Spring Data's `CrudRepository` and `PagingAndSortingRepository` rely on unchecked exceptions
and void return types for operations that can fail or produce ambiguous outcomes. This forces
developers to surround repository calls with try-catch blocks and guess which exceptions
may be thrown — a pattern that is error-prone and inconsistent with functional programming
principles.

fluent-repo-4j introduces **functional repository interfaces** that make outcomes explicit
through a sealed `RepositoryResult<T>` type. Success and failure are first-class values that
can be inspected, composed, and transformed without try-catch.

---

## The Problem with Spring Data's Default Interfaces

### CrudRepository: Only 3 of 12 Methods Are Functional

|            Method             |  Return Type  |      Behavior on Failure / Edge Case       | Functional? |
|-------------------------------|---------------|--------------------------------------------|-------------|
| `save(S)`                     | `S`           | Throws `OptimisticLockingFailureException` | ❌           |
| `saveAll(Iterable<S>)`        | `Iterable<S>` | All-or-nothing; throws on first failure    | ❌           |
| `findById(ID)`                | `Optional<T>` | `Optional.empty()` on absence              | ✅           |
| `existsById(ID)`              | `boolean`     | Returns `false` if not found               | ✅           |
| `findAll()`                   | `Iterable<T>` | Never null, but exceptions are unchecked   | ❌           |
| `findAllById(Iterable<ID>)`   | `Iterable<T>` | Silently drops missing IDs                 | ❌           |
| `count()`                     | `long`        | Throws on infrastructure error             | ❌           |
| `deleteById(ID)`              | `void`        | Silent no-op if entity doesn't exist       | ❌           |
| `delete(T)`                   | `void`        | Silent no-op if entity doesn't exist       | ❌           |
| `deleteAllById(Iterable<ID>)` | `void`        | No feedback on how many were deleted       | ❌           |
| `deleteAll(Iterable<T>)`      | `void`        | No feedback on how many were deleted       | ❌           |
| `deleteAll()`                 | `void`        | No feedback on how many were deleted       | ❌           |

**Only `findById` and `existsById`** communicate their outcomes through the return type.
Everything else requires the caller to either catch exceptions or accept silent behavior.

### PagingAndSortingRepository: 1 of 2 Is Functional

|       Method        |  Return Type  |            Behavior            | Functional? |
|---------------------|---------------|--------------------------------|-------------|
| `findAll(Sort)`     | `Iterable<T>` | Unchecked exception on error   | ❌           |
| `findAll(Pageable)` | `Page<T>`     | Rich return type with metadata | ✅           |

### Key Problems

1. **`save()` throws `OptimisticLockingFailureException`** — a domain-level conflict that the caller should handle explicitly, but the compiler cannot enforce it.
2. **`deleteById()` is void** — the caller cannot distinguish "deleted successfully" from "entity not found."
3. **`findAllById()` silently drops missing IDs** — if you pass `[1, 2, 3]` and only entity 1 exists, you get back just `[entity1]` with no feedback about the missing entries.
4. **`saveAll()` is all-or-nothing** — it throws on the first failure with no partial results.
5. **`deleteAll()` provides no count** — there is no way to know how many entities were actually removed.

---

## How Functional Repositories Solve This

fluent-repo-4j provides **functional counterpart interfaces** where every method returns
`RepositoryResult<T>`:

|         Standard Interface          |             Functional Interface              |
|-------------------------------------|-----------------------------------------------|
| `CrudRepository<T, ID>`             | `FunctionalCrudRepository<T, ID>`             |
| `PagingAndSortingRepository<T, ID>` | `FunctionalPagingAndSortingRepository<T, ID>` |

Every operation returns either `RepositoryResult.Success<T>` or `RepositoryResult.Failure<T>`,
making success and failure explicit at the type level.

---

## RepositoryResult API

`RepositoryResult<T>` is a **sealed interface** with two variants:

```java
public sealed interface RepositoryResult<T> {

    record Success<T>(T value) implements RepositoryResult<T> { }

    record Failure<T>(String message, Throwable cause) implements RepositoryResult<T> { }
}
```

### Combinators

|            Method            |                         Description                          |
|------------------------------|--------------------------------------------------------------|
| `map(Function<T, U>)`        | Transforms the success value; propagates failure unchanged   |
| `fold(onSuccess, onFailure)` | Forces handling of both cases into a single result           |
| `peek(Consumer<T>)`          | Side-effect on success; returns `this` unchanged             |
| `orElseThrow()`              | Extracts the success value or throws `IllegalStateException` |
| `orElse(T)`                  | Returns the success value or a default                       |
| `isSuccess()`                | Returns `true` if this is a `Success`                        |
| `isFailure()`                | Returns `true` if this is a `Failure`                        |

### Key Design Decisions

- **`Success.value` must not be null** — enforced by the record's compact constructor.
- **`Failure.message` must not be null or blank** — every failure must have a description.
- **`Failure.cause` is nullable** — for failures that have no underlying exception.
- **Sealed** — exhaustive `switch` in Java 21+ covers both cases without a default.

---

## Functional Interfaces

### FunctionalCrudRepository

```java
@NoRepositoryBean
public interface FunctionalCrudRepository<T, ID> extends Repository<T, ID> {

    <S extends T> RepositoryResult<S> save(S entity);
    <S extends T> RepositoryResult<List<S>> saveAll(Iterable<S> entities);

    RepositoryResult<Optional<T>> findById(ID id);
    RepositoryResult<Boolean> existsById(ID id);
    RepositoryResult<List<T>> findAll();
    RepositoryResult<List<T>> findAllById(Iterable<ID> ids);
    RepositoryResult<Long> count();

    RepositoryResult<Boolean> deleteById(ID id);       // true = deleted, false = not found
    RepositoryResult<Boolean> delete(T entity);
    RepositoryResult<Long> deleteAllById(Iterable<? extends ID> ids);
    RepositoryResult<Long> deleteAll(Iterable<? extends T> entities);
    RepositoryResult<Long> deleteAll();                 // returns count of deleted entities
}
```

### FunctionalPagingAndSortingRepository

```java
@NoRepositoryBean
public interface FunctionalPagingAndSortingRepository<T, ID> extends Repository<T, ID> {

    RepositoryResult<List<T>> findAll(Sort sort);
    RepositoryResult<Page<T>> findAll(Pageable pageable);
}
```

---

## Derived Query Methods

Functional repositories support Spring Data–style derived query methods. The method name
follows the same conventions as standard repositories (`findBy…`, `countBy…`, `existsBy…`,
`deleteBy…`), but the return type is wrapped in `RepositoryResult`:

```java
public interface UserRepository
        extends FunctionalCrudRepository<User, Long>,
                FunctionalPagingAndSortingRepository<User, Long> {

    RepositoryResult<Optional<User>> findByEmail(String email);
    RepositoryResult<List<User>> findByName(String name);
    RepositoryResult<Long> countByActive(Boolean active);
    RepositoryResult<Boolean> existsByEmail(String email);
}
```

The framework automatically unwraps `RepositoryResult<X>` to determine the inner type (`X`)
and applies the standard PartTree query derivation logic to it.

---

## Error Model

fluent-repo-4j uses a **hybrid error model**:

|      Error Category       |                 Handling                  |                        Example                         |
|---------------------------|-------------------------------------------|--------------------------------------------------------|
| **Infrastructure errors** | Propagate as Spring `DataAccessException` | Connection lost, SQL syntax error                      |
| **Domain-level failures** | Captured in `RepositoryResult.Failure`    | Optimistic locking conflict, save for a missing entity |

This separation means:

- **Infrastructure errors** are truly exceptional, unrecoverable in the current call context, so they remain as exceptions.
- **Domain-level outcomes** are expected edge-cases — the caller should decide what to do: retry, log, or return a user-friendly message.

### Not-Found Semantics

Absence is never a failure:

- `findById(unknownId)` → `Success(Optional.empty())`
- `deleteById(unknownId)` → `Success(false)`

Only real errors produce `Failure`.

---

## Usage Examples

### Basic CRUD

```java
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public String createUser(User user) {
        return userRepository.save(user)
                .fold(
                        saved -> "Created user with ID: " + saved.getId(),
                        failure -> "Save failed: " + failure.message()
                );
    }

    public User findUserOrDefault(Long id, User defaultUser) {
        return userRepository.findById(id)
                .map(opt -> opt.orElse(defaultUser))
                .orElse(defaultUser);
    }
}
```

### Handling Optimistic Locking

```java
RepositoryResult<User> result = userRepository.save(staleUser);

result.fold(
        saved -> {
            log.info("User saved: {}", saved.getId());
            return saved;
        },
        failure -> {
            log.warn("Conflict: {}", failure.message());
            // Retry, notify user, or compensate
            return null;
        }
);
```

### Checking Delete Outcome

```java
RepositoryResult<Boolean> deleteResult = userRepository.deleteById(userId);

deleteResult.peek(deleted -> {
    if (deleted) {
        log.info("User {} deleted.", userId);
    } else {
        log.warn("User {} not found for deletion.", userId);
    }
});
```

### Pattern Matching (Java 21+)

```java
RepositoryResult<Optional<User>> result = userRepository.findByEmail("alice@example.com");

switch (result) {
    case RepositoryResult.Success<Optional<User>>(Optional<User> value) ->
            value.ifPresentOrElse(
                    user -> System.out.println("Found: " + user.getName()),
                    () -> System.out.println("No user with that email")
            );
    case RepositoryResult.Failure<Optional<User>> f ->
            System.err.println("Query failed: " + f.message());
}
```

---

## Mutual Exclusivity

Functional interfaces are **mutually exclusive** with their standard counterparts. A repository
interface must extend one or the other:

```java
// ✅ Valid: functional only
public interface UserRepository
        extends FunctionalCrudRepository<User, Long>,
                FunctionalPagingAndSortingRepository<User, Long> { }

// ✅ Valid: standard only
public interface UserRepository
        extends CrudRepository<User, Long>,
                PagingAndSortingRepository<User, Long> { }

// ❌ Compile error: return type conflict on save(), findAll(), etc.
public interface UserRepository
        extends CrudRepository<User, Long>,
                FunctionalCrudRepository<User, Long> { }
```

This is enforced at compile time — both interfaces declare methods with the same name and
parameter list but different return types, which Java does not allow in a single type hierarchy.

---

## Comparison Table

|            Aspect             |        Standard (`CrudRepository`)         |         Functional (`FunctionalCrudRepository`)         |
|-------------------------------|--------------------------------------------|---------------------------------------------------------|
| `save()` failure              | Throws `OptimisticLockingFailureException` | Returns `Failure("Optimistic locking conflict", cause)` |
| `deleteById()` missing entity | Void, silent no-op                         | Returns `Success(false)`                                |
| `deleteAll()` feedback        | Void, no count                             | Returns `Success(count)`                                |
| `findById()` missing entity   | `Optional.empty()`                         | `Success(Optional.empty())`                             |
| Error handling                | try-catch or `@ControllerAdvice`           | `fold()`, `map()`, pattern matching                     |
| Compiler enforcement          | None (unchecked exceptions)                | Full (sealed type with exhaustive switch)               |
| Infrastructure errors         | `DataAccessException`                      | `DataAccessException` (same — not captured)             |

