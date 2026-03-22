# Dynamic Method Queries

This document describes the **dynamic query derivation** feature in fluent-repo-4j:
declaring repository query methods by name (Spring Data–style) and having them
executed via the fluent-sql-4j DSL.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Supported Operators](#supported-operators)
3. [Usage Examples](#usage-examples)
4. [Return Types](#return-types)
5. [Pagination and Sorting](#pagination-and-sorting)
6. [How to Enable](#how-to-enable)
7. [Security: Parameter Binding](#security-parameter-binding)
8. [Caching](#caching)
9. [Limitations](#limitations)
10. [Package Layout](#package-layout)
11. [Extending: AST Mapper Scaffold](#extending-ast-mapper-scaffold)

---

## Architecture Overview

```
Repository Method Name
        │
        ▼
  PartTreeAdapter          (Spring Data PartTree → QueryDescriptor)
        │
        ▼
  QueryDescriptor          (neutral intermediate model, cached per Method)
        │
        ▼
  QueryDescriptorToDslMapper  (QueryDescriptor + runtime args → SelectBuilder / DeleteBuilder)
        │
        ▼
  FluentRepositoryQuery    (executes PreparedStatement, maps ResultSet → return type)
```

### Key Components

|                          Class                           |                                                                          Role                                                                          |
|----------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PartTreeAdapter`                                        | Parses a method name with Spring Data's `PartTree` and produces a `QueryDescriptor`. Parameter indices are calculated here.                            |
| `QueryDescriptor`                                        | Neutral intermediate model: operation, distinct flag, max results, criterion tree, order-by clauses, Pageable/Sort indices.                            |
| `Criterion` / `PropertyCriterion` / `CompositeCriterion` | Sealed criterion tree representing the WHERE predicate.                                                                                                |
| `QueryDescriptorToDslMapper`                             | Converts the descriptor + runtime arguments into a fluent-sql-4j `SelectBuilder` or `DeleteBuilder` by injecting AST predicates.                       |
| `FluentRepositoryQuery`                                  | Implements Spring Data's `RepositoryQuery`; executes the query and maps results to the declared return type.                                           |
| `FluentQueryLookupStrategy`                              | Implements Spring Data's `QueryLookupStrategy`; resolves each repository method to a `FluentRepositoryQuery`. Registered in `FluentRepositoryFactory`. |

---

## Supported Operators

All standard Spring Data method-name keywords are supported:

|           Keyword(s)           |               SQL equivalent                |    Parameters    |
|--------------------------------|---------------------------------------------|------------------|
| `findBy{Prop}`                 | `WHERE col = ?`                             | 1                |
| `findBy{Prop}Not`              | `WHERE col != ?`                            | 1                |
| `findBy{Prop}LessThan`         | `WHERE col < ?`                             | 1                |
| `findBy{Prop}LessThanEqual`    | `WHERE col <= ?`                            | 1                |
| `findBy{Prop}GreaterThan`      | `WHERE col > ?`                             | 1                |
| `findBy{Prop}GreaterThanEqual` | `WHERE col >= ?`                            | 1                |
| `findBy{Prop}Before`           | `WHERE col < ?`                             | 1                |
| `findBy{Prop}After`            | `WHERE col > ?`                             | 1                |
| `findBy{Prop}Between`          | `WHERE col BETWEEN ? AND ?`                 | 2                |
| `findBy{Prop}IsNull`           | `WHERE col IS NULL`                         | 0                |
| `findBy{Prop}IsNotNull`        | `WHERE col IS NOT NULL`                     | 0                |
| `findBy{Prop}Like`             | `WHERE col LIKE ?`                          | 1                |
| `findBy{Prop}NotLike`          | `WHERE col NOT LIKE ?`                      | 1                |
| `findBy{Prop}StartingWith`     | `WHERE col LIKE ?%`                         | 1                |
| `findBy{Prop}EndingWith`       | `WHERE col LIKE %?`                         | 1                |
| `findBy{Prop}Containing`       | `WHERE col LIKE %?%`                        | 1                |
| `findBy{Prop}NotContaining`    | `WHERE NOT (col LIKE %?%)`                  | 1                |
| `findBy{Prop}In`               | `WHERE col IN (?, ?, ...)`                  | 1 (`Collection`) |
| `findBy{Prop}NotIn`            | `WHERE NOT (col IN (?...))`                 | 1 (`Collection`) |
| `findBy{Prop}True`             | `WHERE col = ?` (bound `true`)              | 0                |
| `findBy{Prop}False`            | `WHERE col = ?` (bound `false`)             | 0                |
| `findBy{A}And{B}`              | `WHERE colA = ? AND colB = ?`               | 2                |
| `findBy{A}Or{B}`               | `WHERE colA = ? OR colB = ?`                | 2                |
| `findBy{A}And{B}Or{C}`         | `WHERE (colA = ? AND colB = ?) OR colC = ?` | 3                |

### IgnoreCase variants

Append `IgnoreCase` to any equality or LIKE keyword:

```java
List<User> findByNameIgnoreCase(String name);
// SQL: WHERE LOWER(name) = 'alice'  (value is lowercased at bind time)

List<User> findByNameContainingIgnoreCase(String fragment);
// SQL: WHERE LOWER(name) LIKE '%alice%'
```

### Distinct / Top / First

```java
List<User> findDistinctByEmail(String email);

List<User> findTop3ByAgeGreaterThan(Integer age);

Optional<User> findFirstByName(String name);
```

### Operation prefixes

```java
countByName(String name)     // returns long
existsByEmail(String email)  // returns boolean
deleteByName(String name)    // returns void or int/long
```

---

## Usage Examples

```java
public interface UserRepository extends CrudRepository<User, Long>,
        PagingAndSortingRepository<User, Long> {

    // Single property
    List<User> findByName(String name);

    // Multiple properties with AND
    List<User> findByNameAndEmail(String name, String email);

    // Comparison
    List<User> findByAgeGreaterThan(Integer age);

    // Range
    List<User> findByAgeBetween(Integer min, Integer max);

    // Case-insensitive substring search
    List<User> findByNameContainingIgnoreCase(String fragment);

    // Null check
    List<User> findByEmailIsNull();

    // In-list
    List<User> findByNameIn(Collection<String> names);

    // Boolean flag
    List<User> findByActiveTrue();

    // Count / exists / delete
    long countByActive(Boolean active);
    boolean existsByEmail(String email);
    void deleteByName(String name);

    // Static ORDER BY in method name
    List<User> findByActiveTrueOrderByNameAsc();

    // Runtime Sort parameter
    List<User> findByActive(Boolean active, Sort sort);

    // Paginated
    Page<User> findByActive(Boolean active, Pageable pageable);

    // Limiting
    List<User> findTop5ByAgeGreaterThan(Integer age);
}
```

---

## Return Types

|      Return type      |                                Behaviour                                 |
|-----------------------|--------------------------------------------------------------------------|
| `List<T>`             | Returns all matching entities.                                           |
| `T` (entity)          | Returns the first result, or `null` if none found.                       |
| `Optional<T>`         | Returns `Optional.of(first)` or `Optional.empty()`.                      |
| `Stream<T>`           | Returns a `Stream` over matching entities.                               |
| `Page<T>`             | Requires a `Pageable` parameter; executes a count query + content query. |
| `Slice<T>`            | Requires a `Pageable` parameter; no count query.                         |
| `long` / `Long`       | For `countBy…` or `deleteBy…` with row-count return.                     |
| `boolean` / `Boolean` | For `existsBy…` methods.                                                 |
| `void`                | For `deleteBy…` methods when no return value is needed.                  |

---

## Pagination and Sorting

### Runtime `Sort`

```java
List<User> findByActive(Boolean active, Sort sort);

// Usage:
repository.findByActive(true, Sort.by(Sort.Direction.DESC, "age"));
```

### Runtime `Pageable`

```java
Page<User> findByActive(Boolean active, Pageable pageable);

// Usage:
repository.findByActive(true, PageRequest.of(0, 10, Sort.by("name")));
```

### Static ORDER BY (from method name)

```java
List<User> findByActiveTrueOrderByNameAsc();
```

---

## How to Enable

Dynamic query methods are enabled automatically when you use `@EnableFluentRepositories`.
No additional configuration is required.

The `FluentRepositoryFactory` registers a `FluentQueryLookupStrategy` which resolves
every method name that follows Spring Data naming conventions. CRUD methods defined
by `CrudRepository` and `PagingAndSortingRepository` continue to be handled by
`FluentRepository` as before.

```java
@SpringBootApplication
@EnableFluentRepositories(basePackages = "com.example.repositories")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

---

## Security: Parameter Binding

**All parameters are bound via prepared-statement placeholders (`?`)** — never
concatenated into the SQL string. This applies to:

- All comparison values (string, number, boolean, date)
- LIKE pattern wildcards (`%`, `_` are part of the bound value, not the SQL template)
- IN list values
- LOWER() expressions for case-insensitive comparisons

The implementation uses `LiteralUtil.createLiteral(value)` (fluent-sql-4j utility)
and the AST predicate constructors (`Comparison`, `Like`, `Between`, `In`) to build
parameterized `PreparedStatement` specs. No string concatenation is used.

---

## Caching

Parsed `QueryDescriptor` objects and `FluentRepositoryQuery` instances are cached
**per `Method`** in `FluentQueryLookupStrategy.cache` (a `ConcurrentHashMap`).

This means:
- Method-name parsing (Spring Data `PartTree`) happens **once** at first invocation.
- Subsequent calls go directly to the cached `FluentRepositoryQuery`, which only
invokes `QueryDescriptorToDslMapper.map(descriptor, args)` to bind the runtime
arguments.

---

## Limitations

- **Flat entity properties only.** Dotted paths such as `findByAddress_City(…)` are
  not supported in this release. An `IllegalArgumentException` is thrown if a nested
  path is encountered.
- **No automatic JOINs.** Relational navigation across entity associations is not
  supported. Join support will be added in a future release.
- **No JPA-specific metadata.** The implementation uses only `@Table`, `@Column`,
  and `@Id` annotations (Jakarta Persistence). JPA metamodels, `@Embedded`, and
  `@OneToMany` / `@ManyToOne` are not read.
- **Unsupported operators.** Geographic operators (`NEAR`, `WITHIN`) and `REGEX` are
  not supported and throw `UnsupportedOperationException`.

---

## Package Layout

```
io.github.auspis.fluentrepo4j
├── query/
│   ├── QueryDescriptor.java          Neutral query descriptor (cached per Method)
│   ├── QueryOperation.java           Enum: FIND, COUNT, EXISTS, DELETE
│   ├── OrderByClause.java            Column + direction pair
│   └── criterion/
│       ├── Criterion.java            Sealed interface: PropertyCriterion | CompositeCriterion
│       ├── CriterionOperator.java    Operator enum (EQUALS, BETWEEN, LIKE, IN, …)
│       ├── PropertyCriterion.java    Leaf: property + operator + param indices
│       └── CompositeCriterion.java   AND/OR of child criteria
├── parse/
│   └── PartTreeAdapter.java          PartTree → QueryDescriptor
├── meta/
│   └── PropertyMetadataProvider.java Property path → column name resolution
└── query/
    ├── mapper/
    │   ├── dsl/
    │   │   └── QueryDescriptorToDslMapper.java  Default strategy: DSL builder
    │   └── ast/
    │       └── QueryDescriptorToAstMapper.java  Scaffold (not used by default)
    └── runtime/
        ├── FluentRepositoryQuery.java    RepositoryQuery implementation
        └── FluentQueryLookupStrategy.java QueryLookupStrategy implementation
```

---

## Extending: AST Mapper Scaffold

`QueryDescriptorToAstMapper` is an intentional scaffold for a future AST-based
mapping strategy. It is **not used** by the default execution pipeline.

Future implementation should:
1. Walk the `QueryDescriptor` criterion tree.
2. Build a fluent-sql-4j AST (`SelectStatement`, `Where`, …) directly, without
using the DSL builder layer.
3. Enable advanced transformations such as query rewriting, plan generation,
and multi-query execution.

To switch to the AST mapper once it is implemented, override
`QueryDescriptorToDslMapper` in your configuration or contribute a pull request.
