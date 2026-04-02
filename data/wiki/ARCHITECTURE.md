# Architecture & Internal Components

This document provides a comprehensive view of fluent-repo-4j's internal architecture, component responsibilities, and data flow.

## Table of Contents

1. [Overview](#overview)
2. [Component Responsibilities](#component-responsibilities)
   - [EnableFluentRepositories](#1-enablefluentrepositories-annotation)
   - [FluentRepositoriesRegistrar](#2-fluentrepositoriesregistrar-importbeandefinitionregistrar)
   - [FluentRepositoryFactoryBean](#3-fluentrepositoryfactorybean-factorybean)
   - [FluentRepositoryFactory](#4-fluentrepositoryfactory-repositoryfactorysupport)
   - [FluentRepository](#5-fluentrepositoryt-id-crudrepository-implementation)
   - [FluentConnectionProvider](#6-fluentconnectionprovider)
   - [FluentEntityInformation](#7-fluententityinformationt-id-entityinformation-implementation)
   - [SaveDecisionResolver](#8-savedecisionresolvert-id)
   - [SaveAction](#9-saveaction)
   - [FluentEntityRowMapper](#10-fluententityrowmappert)
   - [FluentEntityWriter](#11-fluententitywritert)
   - [NamingUtils](#12-namingutils)
   - [DialectDetector](#13-dialectdetector)
   - [FluentRepositoriesAutoConfiguration](#14-fluentrepositoriesautoconfiguration)
3. [Data Flow: Save Operation](#data-flow-save-operation)
4. [Data Flow: FindById Operation](#data-flow-findbyid-operation)
5. [Transaction Binding & Connection Lifecycle](#transaction-binding--connection-lifecycle)
6. [Entity Metadata Caching](#entity-metadata-caching)
7. [Exception Handling](#exception-handling)
8. [Limitations & Design Decisions](#limitations--design-decisions)
9. [Extension Points](#extension-points)
10. [Testing & Debugging](#testing--debugging)

---

## Overview

fluent-repo-4j is a Spring Boot integration layer for **pure JDBC-based repositories**. It bridges the gap between Spring Data Commons (the repository abstraction) and raw JDBC, providing:

- Automatic repository bean creation via Spring Data's factory pattern
- Entity metadata extraction from Jakarta Persistence annotations
- Automatic mapping from ResultSet rows to entity instances
- Spring transaction integration via `DataSourceUtils`
- SQL generation using the fluent-sql-4j DSL

**Design principle**: Minimal Spring coupling while leveraging Spring Data's proven extension points.

---

## Component Responsibilities

### 1. EnableFluentRepositories (Annotation)

**Location**: `io.github.auspis.fluentrepo4j.config.EnableFluentRepositories`

**Purpose**: Entry point for repository scanning and auto-configuration.

**How it works**:
- User places `@EnableFluentRepositories` on a `@Configuration` class (or Spring Boot auto-discovers it via autoconfiguration)
- The annotation imports `FluentRepositoriesRegistrar` via `@Import`

```java
@Configuration
@EnableFluentRepositories(basePackages = "com.example.repositories")
public class RepositoryConfig {
}
```

For multi-datasource applications, each repository group can also declare explicit infrastructure refs:

```java
@Configuration
@EnableFluentRepositories(
   basePackages = "com.example.billing",
   dataSourceRef = "billingDataSource",
   transactionManagerRef = "billingTransactionManager")
class BillingRepositoryConfig {
}
```

---

### 2. FluentRepositoriesRegistrar (ImportBeanDefinitionRegistrar)

**Location**: `io.github.auspis.fluentrepo4j.config.FluentRepositoriesRegistrar`

**Purpose**: Implements Spring's `ImportBeanDefinitionRegistrar` to scan the classpath for `CrudRepository` interface definitions and register bean definitions.

**How it works**:
1. Scans basePackages for interfaces extending `CrudRepository`
2. For each interface found, creates a `BeanDefinition` pointing to `FluentRepositoryFactoryBean`
3. Registers the bean definition in the Spring application context

**Key method**:

```java
void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, 
                             BeanDefinitionRegistry registry)
```

---

### 3. FluentRepositoryFactoryBean (FactoryBean)

**Location**: `io.github.auspis.fluentrepo4j.repository.FluentRepositoryFactoryBean`

**Purpose**: Spring FactoryBean that instantiates `FluentRepositoryFactory` and creates the actual repository implementation.

**How it works**:
1. Resolves infrastructure for the repository group using explicit refs when present
2. Creates a `FluentRepositoryFactory` passing the `FluentConnectionProvider` and DSL (with the connection provider potentially derived from a `DataSource`)
3. `getObject()` delegates to the factory to create the `FluentRepository<T, ID>` instance
4. Spring Data wraps the implementation with a proxy for transaction handling and method interception

**Why FactoryBean?** Allows initialization of complex bean dependencies (connection provider / DataSource resolution, DSL instantiation) before the repository bean is created.

**Resolution precedence**:

1. `connectionProviderRef` wins over `dataSourceRef`
2. `dslRef` wins over dialect auto-detection
3. If no explicit refs are configured, fluent-repo-4j falls back to a single candidate or `@Primary`
4. If multiple candidates remain ambiguous, startup fails with a clear error message

---

### 4. FluentRepositoryFactory (RepositoryFactorySupport)

**Location**: `io.github.auspis.fluentrepo4j.repository.FluentRepositoryFactory`

**Purpose**: Creates `FluentRepository<T, ID>` instances for a given entity type. Implements Spring Data's `RepositoryFactorySupport` SPI.

**How it works**:
1. Analyzes the repository interface (e.g., `UserRepository extends CrudRepository<User, Long>`)
2. Extracts entity type and ID type via Spring Data metadata
3. Instantiates `FluentEntityInformation<User, Long>` to get metadata (table name, columns, ID strategy)
4. Returns a new `FluentRepository<User, Long>` prepared for that entity
5. Injects `FluentRepositoryContext<T>` (DSL + connection provider + row mapper + writer) into custom fragment implementations that implement `FluentRepositoryContextAware<T>`, before the repository proxy is returned. Includes singleton overwrite protection for multi-datasource configurations.

**Key methods**:

```java
protected Object getTargetRepository(RepositoryInformation repositoryInformation)

public <T> T getRepository(Class<T> repositoryInterface, RepositoryFragments fragments)
// ↳ Iterates fragments, injects FluentRepositoryContext<T> into FluentRepositoryContextAware impls
```

---

### 5. FluentRepository<T, ID> (CrudRepository Implementation)

**Location**: `io.github.auspis.fluentrepo4j.repository.FluentRepository`

**Purpose**: The actual CRUD implementation. Executes SQL operations against the database using fluent-sql-4j DSL and Spring's `DataSourceUtils`.

**Implemented Methods**:
- `save(T)` – INSERT or UPDATE delegating to `SaveDecisionResolver`
- `findById(ID)` – Single entity lookup
- `findAll()` – Retrieve all entities
- `count()` – Row count for the table
- `deleteById(ID)` – DELETE by primary key
- `existsById(ID)` – Check existence
- `delete(T)` – DELETE by entity (uses `getId()`)
- `deleteAll()` – DELETE all rows (loop-based, not batch)

**Save logic**:

```java
public <S extends T> S save(S entity) {
    return switch (saveDecisionResolver.apply(entity)) {
        case INSERT_PROVIDED_ID -> insertWithProvidedId(entity);
        case INSERT_AUTO_ID     -> insertWithIdentity(entity);
        case UPDATE             -> update(entity);
        case ERROR             -> throw new IllegalStateException(...);
    };
}
```

The decision is entirely delegated to `SaveDecisionResolver`. `update()` checks the affected row count and throws `OptimisticLockingFailureException` if the row has disappeared between the existsById check and the update.

**Connection handling**:
- Obtains `Connection` via `FluentConnectionProvider.getConnection()`
- If `@Transactional` is active, `DataSourceUtils` binds the connection to the transaction
- Executes SQL using fluent-sql-4j's prepared statement API
- Connection is automatically released by Spring when the transaction ends

---

### 6. FluentConnectionProvider

**Location**: `io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider`

**Purpose**: Abstraction for connection management. Wraps Spring's `DataSourceUtils` to provide transaction-aware connection access.

**How it works**:
- Stores the `DataSource`
- `getConnection()` calls `DataSourceUtils.getConnection(dataSource)`
- If a transaction is active, returns the transactional connection
- Otherwise, returns a new auto-commit connection
- `releaseConnection(conn)` calls `DataSourceUtils.releaseConnection(conn, dataSource)`
- If connection is transactional, does nothing (Spring manages it)
- If auto-commit, closes the connection

**Why wrap DataSourceUtils?** Provides a clean, testable interface for `FluentRepository`.

---

### 7. FluentEntityInformation<T, ID> (EntityInformation Implementation)

**Location**: `io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation`

**Purpose**: Extracts and caches metadata about an entity class using Jakarta Persistence annotations.

**Extracted Metadata**:
- **Table name**: From `@Table(name = "...")`, or default to snake_case of class name
- **Columns**: From `@Column(name = "...")` annotations, or auto-derived field names
- **ID field**: Identified by `@Id` annotation
- **ID generation strategy**: Determined by `@GeneratedValue(strategy = ...)` annotation
- `@GeneratedValue(IDENTITY)` → `IdGenerationStrategy.IDENTITY`
- No annotation → `IdGenerationStrategy.PROVIDED`
- **Transient fields**: Identified by `@Transient` annotation (excluded from mapping)

**Key methods**:

```java
String getTableName()              // "users", "orders", etc.
String getColumnName(Field field)  // "user_name" for field "userName"
Object getId(T entity)             // Retrieve the ID value
void setId(T entity, ID id)        // Set the ID value via reflection
boolean isNew(T entity)            // Check if entity is new (not persisted)
```

**isNew() logic** (Spring Data SPI contract, used by callers outside save()):

```
if entity instanceof Persistable:
  return entity.isNew()  // developer has full control
else:
  return entity.getId() == null
```

Note: `save()` does not use `isNew()` directly — it delegates to `SaveDecisionResolver`, which applies richer logic (see below).

---

### 8. SaveDecisionResolver<T, ID>

**Location**: `io.github.auspis.fluentrepo4j.repository.SaveDecisionResolver`

**Purpose**: Determines the `SaveAction` to perform for a given entity. Implements `Function<T, SaveAction>` — constructed once per repository with fixed dependencies, stateless at application time.

**Constructor dependencies** (fixed at construction time):
- `FluentEntityInformation<T, ID>` — entity metadata
- `Predicate<ID> existsById` — DB check (bound to `FluentRepository.existsById`)

**Decision logic**:

|                       Condition                       |                            Result                            |
|-------------------------------------------------------|--------------------------------------------------------------|
| Entity implements `Persistable` and `isNew()` = true  | `INSERT_PROVIDED_ID` or `INSERT_AUTO_ID` (based on strategy) |
| Entity implements `Persistable` and `isNew()` = false | `UPDATE` (no DB call)                                        |
| ID is null                                            | `INSERT_PROVIDED_ID` or `INSERT_AUTO_ID` (based on strategy) |
| ID non-null and exists in DB                          | `UPDATE`                                                     |
| ID non-null, not in DB, strategy = PROVIDED           | `INSERT_PROVIDED_ID`                                         |
| ID non-null, not in DB, strategy = IDENTITY           | `ERROR` (inconsistent state)                                 |

**Key property**: `Persistable` path never calls `existsById` — the entity declares its own state.

---

### 9. SaveAction

**Location**: `io.github.auspis.fluentrepo4j.repository.SaveAction`

**Purpose**: Enum representing the action to perform in `save()`. Combines operation (INSERT vs UPDATE) with ID mechanism, so `save()` can dispatch with a single exhaustive switch.

```java
public enum SaveAction {
    INSERT_PROVIDED_ID,  // INSERT using app-provided ID
    INSERT_AUTO_ID,      // INSERT, let DB generate ID
    UPDATE,              // UPDATE WHERE id = ?
    ERROR                // Inconsistent state, throw
}
```

**Extensibility**: adding a new `IdGenerationStrategy` (e.g., `SEQUENCE`) requires adding one value here and one case in `SaveDecisionResolver.insertActionFor()` — the switch in `save()` will fail to compile until the new case is handled.

---

### 10. FluentEntityRowMapper<T>

**Location**: `io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper`

**Purpose**: Implements Spring's `RowMapper<T>` to convert `ResultSet` rows into entity instances.

**How it works**:
1. Receives a `ResultSet` from a query result
2. For each column in the ResultSet:
- Gets the column name
- Finds the corresponding field in the entity class
- Retrieves the value from the ResultSet using the correct type
- Uses `DslTypeDispatcher` to handle type conversion
3. Creates an entity instance via reflection and populates fields

**Supported types**: String, Long, Integer, Short, Byte, Double, Float, BigDecimal, Boolean, LocalDate, LocalDateTime, UUID.

**Type conversion** (via `DslTypeDispatcher`):
- `ResultSet.getLong()` → `Long`, `Integer`, `Short`, `Byte`, `UUID` (if stored as Long)
- `ResultSet.getString()` → `String`, `UUID` (if stored as String)
- `ResultSet.getBoolean()` → `Boolean`
- `ResultSet.getDate()` → `LocalDate`
- `ResultSet.getTimestamp()` → `LocalDateTime`

---

### 11. FluentEntityWriter<T>

**Location**: `io.github.auspis.fluentrepo4j.mapping.FluentEntityWriter`

**Purpose**: Writes entity field values to `PreparedStatement` parameters for INSERT/UPDATE operations.

**How it works**:
1. Iterates over entity fields (excluding `@Transient` and generated fields)
2. For each field, retrieves the value from the entity instance
3. Uses `DslTypeDispatcher` to determine the correct `setXXX()` method
4. Binds the value to the prepared statement parameter

**Example**:

```
Entity: User(id=1, name="Alice", email="alice@example.com")
SQL: INSERT INTO users (id, name, email) VALUES (?, ?, ?)
Parameters: [1, "Alice", "alice@example.com"]
```

---

### 12. NamingUtils

**Location**: `io.github.auspis.fluentrepo4j.mapping.NamingUtils`

**Purpose**: Implements convention-based naming: convert Java camelCase field names to snake_case column names.

**Examples**:
- `userName` → `user_name`
- `email` → `email`
- `createdAt` → `created_at`

**Used by**: `FluentEntityInformation` for fields without explicit `@Column` annotation.

---

### 13. DialectDetector

**Location**: `io.github.auspis.fluentrepo4j.dialect.DialectDetector`

**Purpose**: Auto-detects the database dialect from DataSource metadata, used to configure fluent-sql-4j.

**How it works**:
1. Gets the database product name from `DataSource.getConnection().getMetaData().getDatabaseProductName()`
2. Maps product name to fluent-sql-4j dialect (e.g., "MySQL" → `MySQL57Dialect`)
3. Falls back to standard SQL 2008 if unknown

---

### 14. FluentRepositoriesAutoConfiguration

**Location**: `io.github.auspis.fluentrepo4j.autoconfigure.FluentRepositoriesAutoConfiguration`

**Purpose**: Spring Boot auto-configuration that enables repository scanning without explicit `@EnableFluentRepositories` annotation.

**How it works**:
- Declares a conditional `@Configuration` that imports `EnableFluentRepositories` automatically
- Only activates if Spring Data Commons and Spring JDBC are on the classpath

---

## Data Flow: Save Operation

```
1. Application calls: repository.save(user)
                        ↓
2. Spring Data proxy intercepts the call
                        ↓
3. Proxy delegates to: FluentRepository.save(user)
                        ↓
4. SaveDecisionResolver.apply(user)  →  SaveAction

   Case A: user implements Persistable
     user.isNew() = true  →  INSERT_PROVIDED_ID or INSERT_AUTO_ID (no DB call)
     user.isNew() = false →  UPDATE (no DB call)

   Case B: standard entity (no Persistable)
     user.id == null      →  INSERT_PROVIDED_ID or INSERT_AUTO_ID
     user.id != null:
       existsById(id) = true  →  UPDATE
       existsById(id) = false:
         strategy = PROVIDED  →  INSERT_PROVIDED_ID
         strategy = IDENTITY  →  ERROR
                        ↓
5. switch(SaveAction):
   INSERT_PROVIDED_ID  →  insertWithProvidedId(entity)
   INSERT_AUTO_ID      →  insertWithIdentity(entity)  (reads generated key, sets entity.id)
   UPDATE              →  update(entity)  (throws OptimisticLockingFailureException if row count = 0)
   ERROR               →  throw IllegalStateException
                        ↓
6. Connection management:
   - FluentConnectionProvider.getConnection() gets a connection
   - If @Transactional active: DataSourceUtils returns the transactional connection
   - If no transaction: DataSourceUtils returns a new auto-commit connection
   - After query execution: DataSourceUtils releases the connection
   - If transactional: connection remains open until transaction commits/rolls back
   - If auto-commit: connection is closed immediately
                        ↓
7. Entity is returned to the caller
```

---

## Data Flow: FindById Operation

```
1. Application calls: repository.findById(1L)
                        ↓
2. Spring Data proxy intercepts the call
                        ↓
3. Proxy delegates to: FluentRepository.findById(1L)
                        ↓
4. FluentRepository:
   - Builds SQL: SELECT * FROM users WHERE id = ?
   - Parameters: [1]
                        ↓
5. Connection handling:
   - FluentConnectionProvider.getConnection() → gets connection (transactional or auto-commit)
   - Executes query → gets ResultSet
   - FluentEntityRowMapper maps ResultSet rows to User instances
   - (For findById, returns at most 1 row)
                        ↓
6. FluentEntityRowMapper:
   - Creates new User instance
   - Iterates over ResultSet columns: id, name, email, age
   - Uses DslTypeDispatcher to get correct type for each column
   - Sets field values via reflection
   - Returns populated User instance
                        ↓
7. Simple returns: Optional<User>
```

---

## Transaction Binding & Connection Lifecycle

**Key principle**: Connections are managed **entirely by Spring's `DataSourceUtils`**. The library does not implement custom ThreadLocal or ScopedValue storage.

### With @Transactional

```
1. Application calls a method marked @Transactional
                        ↓
2. Spring's TransactionInterceptor intercepts the call
                        ↓
3. TransactionAspect:
   - Gets a connection from DataSource
   - Stores it in the transactional context (ThreadLocal or ScopedValue, Spring's choice)
   - Begins transaction (or joins existing)
                        ↓
4. During method execution:
   - repository.save() calls FluentConnectionProvider.getConnection()
   - DataSourceUtils checks: is there an active transaction?
   - YES → returns the same connection from transactional context
   - Methods on this connection see consistent, isolated data
                        ↓
5. After method execution:
   - Spring's TransactionAspect:
   - Commits or rolls back the transaction
   - Releases the connection back to the DataSource
```

### Without @Transactional (Auto-Commit)

```
1. Application calls repository.findById(1L) (no @Transactional)
                        ↓
2. FluentRepository.findById() calls FluentConnectionProvider.getConnection()
                        ↓
3. DataSourceUtils checks: is there an active transaction?
   NO → DataSource.getConnection() returns a new connection (auto-commit mode)
                        ↓
4. Query executes (auto-commits immediately after execution)
                        ↓
5. FluentConnectionProvider.releaseConnection() → connection is closed
```

---

## Entity Metadata Caching

`FluentEntityInformation` caches metadata per entity class to avoid repeated reflection:

- Table name
- Column mapping (field → column)
- ID field reference
- Transient fields
- ID generation strategy

This is computed once during application startup and reused for every operation.

---

## Exception Handling

All `SQLException` exceptions are translated to Spring's `DataAccessException` hierarchy:

```java
try {
    // JDBC operation
} catch (SQLException e) {
    throw new DataAccessException(...) // Spring Data translation
}
```

This allows application code to handle database errors using Spring's exception abstraction, independent of the JDBC driver implementation.

---

## Limitations & Design Decisions

1. **No Persistence Context**: Unlike Hibernate, there is no first-level cache. Each query returns fresh object instances. This is intentional: JDBC is stateless by design.

2. **Single-Table Mapping**: Only flat entity-to-table mapping is supported. No relationships, lazy loading, or join strategies. This keeps the library simple and fast.

3. **SQL Generation via Fluent-SQL-4J**: The library does not generate SQL directly. Instead, it delegates to the fluent-sql-4j library, which provides DSL-based SQL construction. This ensures compatibility with multiple databases.

4. **Custom Query Methods via Derivation or Fragments**: Beyond CRUD, the library supports Spring Data–style method-name derivation (`findByEmail()`, `countByActive()`, etc.) and custom fragment implementations via `FluentRepositoryContextAware<T>`. See [DYNAMIC_METHOD_QUERIES.md](DYNAMIC_METHOD_QUERIES.md) and [USAGE_EXAMPLES.md](USAGE_EXAMPLES.md) for details.

5. **ID Generation Limited to PROVIDED and IDENTITY**: Only two strategies are currently implemented:

   - `PROVIDED`: Application sets the ID
   - `IDENTITY`: Database auto-increment

   `SEQUENCE` support (for databases like PostgreSQL) is planned for future releases.

---

## Extension Points

The library is designed for extensibility:

1. **Custom Fragments**: Add custom repository methods by implementing an interface and providing an implementation class.
2. **Entity Metadata**: Subclass `FluentEntityInformation` to customize metadata extraction.
3. **Row Mapping**: Subclass `FluentEntityRowMapper` to add custom type converters.
4. **Connection Provider**: Implement `ConnectionProvider` interface for alternative connection strategies (though Spring's `DataSourceUtils` is recommended).

---

## Testing & Debugging

When testing repositories:

1. **Use H2 in-memory database** for test isolation (see main `README.md` for examples).
2. **Use `@Transactional` on tests** to rollback after each test (Spring's standard practice).
3. **Enable SQL logging** in application properties:

   ```yaml
   logging.level.org.springframework.jdbc: DEBUG
   ```

