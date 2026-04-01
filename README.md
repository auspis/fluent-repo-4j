# fluent-repo-4j - Lightweight Spring Boot Repositories - DSL for simple, type-safe and maintainable SQL

A lightweight Spring Boot library for implementing the **Repository Pattern** with pure JDBC and the fluent-sql-4j DSL. Write type-safe, declarative database queries without ORM overhead.

## Features

✅ **Spring Boot Auto-Configuration** - Zero boilerplate; `@EnableFluentRepositories` scans and creates repository beans  
✅ **Pure JDBC** - Full SQL control via fluent-sql-4j DSL; no ORM complexity  
✅ **Spring Transaction Integration** - Automatic binding via `DataSourceUtils`; `@Transactional` works seamlessly  
✅ **Simple Entity Mapping** - Jakarta Persistence annotations (`@Table`, `@Column`, `@Id`); automatic snake_case conversion  
✅ **ID Generation Strategies** - Support for application-provided IDs and database auto-increment (`@GeneratedValue(IDENTITY)`)  
✅ **Type Conversion** - Automatic mapping: strings, numbers, booleans, dates (LocalDate, LocalDateTime)  
✅ **Exception Translation** - SQL exceptions automatically translated to Spring's `DataAccessException`  
✅ **Multi-DataSource Repository Groups** - Bind different repository groups to different `DataSource` beans using explicit Spring-style refs  
✅ **Custom Query Fragments** - Implement custom queries using the fluent-sql-4j DSL via Spring Data's fragment convention; opt-in context injection with multi-datasource isolation

---

## Quick Start

Include this library as a dependency and Spring Boot auto-configures everything!

```xml
<dependency>
    <groupId>io.github.auspis</groupId>
    <artifactId>fluent-repo-4j</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 1. Define Your Entity

Use Jakarta Persistence annotations. The `@Id` field is required; map the ID generation strategy if needed.

```java
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // Database auto-increment
    private Long id;
    
    @Column(name = "name")
    private String name;
    
    @Column(name = "email")
    private String email;
    
    private int age;  // Field without @Column → auto-mapped to 'age' column
    
    // Constructors, getters, setters...
}
```

### 2. Create a Repository Interface

Extend `CrudRepository<Entity, ID>`. CRUD methods are inherited automatically.

`fluent-repo-4j` supports Spring Data method-name query derivation (PartTree-style) out of the box.

```java
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {
    // Inherited methods: save(), findById(), findAll(), count(), deleteById(), etc.
    
    List<User> findByEmailIgnoreCase(String email);
    List<User> findByNameContainingIgnoreCase(String name);
    List<User> findByAgeGreaterThan(Integer minAge);
}
```

```java
@Service
public class UserService {
    private final UserRepository userRepository;
    public UserService(UserRepository userRepository) { this.userRepository = userRepository; }

    public List<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    public List<User> searchByName(String fragment) {
        return userRepository.findByNameContainingIgnoreCase(fragment);
    }

    public List<User> findOlderThan(int age) {
        return userRepository.findByAgeGreaterThan(age);
    }
}
```

For full operator details (`And`, `Or`, `Between`, `Top`, `Pageable`, etc.), see [DYNAMIC_METHOD_QUERIES](data/wiki/DYNAMIC_METHOD_QUERIES.md).

### 3. Enable Repositories

Register the repository package explicitly.

```java
import io.github.auspis.fluentrepo4j.config.EnableFluentRepositories;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFluentRepositories(basePackageClasses = UserRepository.class)
class RepositoryConfiguration {}
```

### 4. Inject and Use

```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Transactional
    public User createUser(String name, String email, int age) {
        User user = new User(name, email, age);
        // ID is auto-generated; save() performs INSERT
        return userRepository.save(user);
    }
    
    public User findUser(Long id) {
        return userRepository.findById(id).orElseThrow();
    }
    
    @Transactional
    public User updateUser(Long id, String newName) {
        User user = findUser(id);
        user.setName(newName);
        // save() detects ID exists → performs UPDATE
        return userRepository.save(user);
    }
    
    public List<User> allUsers() {
        return (List<User>) userRepository.findAll();
    }
    
    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
```

### 5. Configure DataSource

In `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/myapp
    username: root
    password: secret
    driver-class-name: com.mysql.cj.jdbc.Driver
```

**That's it!** The library:
- ✅ Auto-detects the database dialect from DataSource metadata  
- ✅ Scans for `CrudRepository` interfaces and creates beans  
- ✅ Binds connections to Spring transactions automatically via `DataSourceUtils`  
- ✅ Maps entities to tables using Jakarta Persistence annotations

### 6. Configure Multiple DataSources

For multi-datasource applications, configure one `@EnableFluentRepositories` block per repository group.

See [data/wiki/USAGE_EXAMPLES.md](data/wiki/USAGE_EXAMPLES.md) for complete examples, decision matrix, and binding strategies.

---

## ID Generation Strategies

How does the library decide whether to INSERT or UPDATE? A `SaveDecisionResolver` evaluates each `save()` call and returns one of four actions: `INSERT_PROVIDED_ID`, `INSERT_AUTO_ID`, `UPDATE`, or `ERROR`.

### Strategy 1: Application-Provided ID (Default)

The **application** sets the ID before calling `save()`. No `@GeneratedValue` annotation is needed.

```java
@Table(name = "events")
public class Event {
    @Id
    private UUID id;  // No @GeneratedValue
    
    private String description;
}

// Usage:
Event event = new Event(UUID.randomUUID(), "Something happened");
repository.save(event);  // id != null, not in DB → INSERT_PROVIDED_ID
```

**Decision logic**: id is non-null → `existsById()` check → if not found, INSERT; if found, UPDATE.  
**Best for**: UUIDs, business keys, any scenario where the app generates the ID.

### Strategy 2: Database Auto-Increment

Let the **database** generate the ID using auto-increment / identity columns.

```java
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Database generates this
    
    private String name;
}

// Usage:
User user = new User("Alice");  // id = null
repository.save(user);  // id == null → INSERT_AUTO_ID, database sets id on the entity
```

**Decision logic**: id is null → `INSERT_AUTO_ID`. The generated key is read back and set on the entity.  
**Error case**: if an entity with `@GeneratedValue(IDENTITY)` has a non-null id that does not exist in the DB, `save()` throws `IllegalStateException` — this is an inconsistent state.  
**Best for**: Sequential Long IDs (IDENTITY / SERIAL / AUTO_INCREMENT columns).

### Strategy 3: Custom `isNew()` Logic via `FluentPersistable<ID>`

For complete control over the new/existing distinction, implement `FluentPersistable<ID>`. The `SaveDecisionResolver` honours `isNew()` directly and skips the `existsById()` database call.

```java
import io.github.auspis.fluentrepo4j.FluentPersistable;

@Table(name = "products")
public class Product implements FluentPersistable<Integer> {
    @Id
    private Integer id;

    private String name;
    private Double price;

    @Transient
    private boolean isNewEntity = true;

    @Override
    public boolean isNew() {
        return isNewEntity;
    }

    @Override
    public void markPersisted() {
        this.isNewEntity = false;
    }
}

// Insert:
Product p = new Product(1, "Widget", 19.99);
repository.save(p);  // isNew() = true → INSERT_PROVIDED_ID (no DB call)

// Update:
p.setPrice(24.99);
repository.save(p);  // isNew() = false → UPDATE (no DB call)
```

**Decision logic**: delegates entirely to `entity.isNew()` — no `existsById()` call.  
**Note**: `@PostLoad` / `@PostPersist` are JPA callbacks and are **not** fired in pure JDBC mode. When the entity implements `FluentPersistable`, fluent-repo-4j calls `markPersisted()` automatically after `save()` and after loading from the database.  
**Best for**: explicit state control, complex ID schemes, or performance-sensitive code that avoids the `existsById()` round-trip.

---

## Custom Query Fragments

When CRUD operations and derived query methods aren't enough, you can write custom queries using the fluent-sql-4j DSL via **Spring Data's fragment convention**.

### 1. Define a Fragment Interface

```java
public interface UserCustomQueries {
    List<User> findUsersByNameContaining(String namePart);
    long countActiveUsers();
}
```

### 2. Implement the Fragment with Fluent DSL

Implement `FluentRepositoryContextAware<User>` to receive the repository-specific DSL, connection provider, row mapper, and entity writer:

```java
public class UserCustomQueriesImpl implements UserCustomQueries, FluentRepositoryContextAware<User> {

    private FluentRepositoryContext<User> context;

    @Override
    public FluentRepositoryContext<User> getFluentRepositoryContext() {
        return context;
    }

    @Override
    public void setFluentRepositoryContext(FluentRepositoryContext<User> context) {
        this.context = context;
    }

    @Override
    public List<User> findUsersByNameContaining(String namePart) {
        DSL dsl = context.dsl();
        Connection conn = context.connectionProvider().getConnection();
        try {
            PreparedStatement ps = dsl.selectAll()
                    .from("users")
                    .where().column("name").like("%" + namePart + "%")
                    .build(conn);
            try (ps; ResultSet rs = ps.executeQuery()) {
                List<User> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(context.rowMapper().mapRow(rs, rs.getRow()));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Query failed", e);
        } finally {
            context.connectionProvider().releaseConnection(conn);
        }
    }

    @Override
    public long countActiveUsers() {
        DSL dsl = context.dsl();
        Connection conn = context.connectionProvider().getConnection();
        try {
            PreparedStatement ps = dsl.select().countStar()
                    .from("users").where().column("active").eq(true)
                    .build(conn);
            try (ps; ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Query failed", e);
        } finally {
            context.connectionProvider().releaseConnection(conn);
        }
    }
}
```

### 3. Extend Your Repository

```java
public interface UserRepository extends CrudRepository<User, Long>, UserCustomQueries {
    // Inherits: save(), findById(), findAll(), etc.
    // Plus custom: findUsersByNameContaining(), countActiveUsers()
}
```

### Key Points

- **Naming convention**: The implementation class must be named `{FragmentInterface}Impl` (e.g., `UserCustomQueriesImpl`). Customizable via `@EnableFluentRepositories(repositoryImplementationPostfix = "Custom")`.
- **Opt-in**: `FluentRepositoryContextAware` is optional. Fragments that don't implement it work normally — the injection is a no-op.
- **Type-safe mapping**: The context provides `rowMapper()` and `writer()` typed to your entity, eliminating manual `ResultSet` mapping.
- **Multi-datasource safe**: Each fragment receives the `FluentRepositoryContext` (DSL + connection provider + entity mapper/writer) bound to its repository group. In multi-datasource setups, fragments on `@EnableFluentRepositories(dataSourceRef = "primary")` get the primary DSL, and fragments on `dataSourceRef = "secondary"` get the secondary DSL automatically.
- **Singleton overwrite protection**: If a fragment bean is shared across repository groups with different datasources, an `IllegalStateException` is thrown at bootstrap time to prevent silent datasource mismatch.
- **Multiple fragments**: A single repository can extend multiple fragment interfaces, mixing aware and non-aware fragments.

---

## Data Types Supported

The library automatically converts ResultSet columns to Java types:

|         Java Type          | Supported |                   Notes                   |
|----------------------------|-----------|-------------------------------------------|
| String                     | ✅         | VARCHAR, TEXT, CHAR                       |
| Long, Integer, Short, Byte | ✅         | BIGINT, INT, SMALLINT, TINYINT            |
| Double, Float, BigDecimal  | ✅         | DOUBLE, FLOAT, DECIMAL                    |
| Boolean                    | ✅         | BOOLEAN, BIT (0/1 converts to false/true) |
| LocalDate                  | ✅         | DATE                                      |
| LocalDateTime              | ✅         | TIMESTAMP                                 |
| UUID                       | ✅         | VARCHAR/CHAR (stored as string)           |

---

## Architecture Snapshot

How does the library work?

1. **Application startup**: Spring Boot detects `@EnableFluentRepositories` (or auto-configuration) and triggers repository scanning.
2. **Repository bean creation**: For each `CrudRepository` interface, a `FluentRepositoryFactoryBean` creates a `FluentRepository<Entity, ID>` implementation and wraps it with Spring Data's proxy.
3. **Method invocation**: When you call `repository.save(entity)`, the proxy invokes `FluentRepository.save()`.
4. **Save logic via SaveDecisionResolver**:
   - If entity implements `Persistable`: uses `entity.isNew()` directly, no DB call
   - If id is null: INSERT (auto-id or provided, based on strategy)
   - If id is non-null: `existsById()` check → UPDATE or INSERT (PROVIDED) or error (IDENTITY)
   - `update()` checks the affected row count; throws `OptimisticLockingFailureException` if the row has disappeared
5. **SQL execution**: `FluentRepository` builds SQL using fluent-sql-4j, prepares statements via `FluentConnectionProvider`, and executes against the DataSource.
6. **Transaction binding**: Connections are obtained via Spring's `DataSourceUtils`, automatically bound to the active `@Transactional` scope.
7. **Results mapping**: `FluentEntityRowMapper` converts ResultSet rows to entity instances using Jakarta Persistence metadata.

**For detailed component descriptions and data flow diagrams**, see [data/wiki/ARCHITECTURE.md](data/wiki/ARCHITECTURE.md).

---

## Supported vs Not Supported

|                                Feature                                 |     Status      |                                                    Notes                                                    |
|------------------------------------------------------------------------|-----------------|-------------------------------------------------------------------------------------------------------------|
| CRUD operations (`save`, `findById`, `findAll`, `count`, `deleteById`) | ✅ Supported     | Core functionality built-in                                                                                 |
| `@Transactional` integration                                           | ✅ Supported     | Automatic connection binding via Spring                                                                     |
| `@GeneratedValue(IDENTITY)`                                            | ✅ Supported     | Database auto-increment IDs                                                                                 |
| Application-provided IDs                                               | ✅ Supported     | Set ID before `save()`                                                                                      |
| `Persistable<ID>` for custom `isNew()` logic                           | ✅ Supported     | Fine-grained control over insert/update                                                                     |
| Simple entity mapping (Jakarta Persistence annotations)                | ✅ Supported     | `@Table`, `@Column`, `@Id`, `@GeneratedValue`, `@Transient`                                                 |
| Exception translation to `DataAccessException`                         | ✅ Supported     | Automatic SQL exception handling                                                                            |
| Multi-datasource repository groups                                     | ✅ Supported     | Configure one `@EnableFluentRepositories` block per repository group                                        |
| Custom query methods via fragments                                     | ✅ Supported     | Implement `FluentRepositoryContextAware<T>` fragments for DSL-powered custom queries with type-safe mapping |
| Query method derivation (e.g., PartTree)                               | ✅ Supported     | Implement `FluentRepositoryContextAware` fragments for DSL-powered custom queries release                   |
| Object relationships (one-to-many, many-to-many)                       | ❌ Not Supported | Use separate repositories and explicit queries                                                              |
| `@GeneratedValue(SEQUENCE)`                                            | ❌ Not Supported | Planned for future release                                                                                  |
| Persistence context / first-level cache                                | ❌ Not Supported | Not applicable to JDBC; each query returns fresh objects                                                    |

---

## Current Limitations

This library focuses on **simple CRUD operations for single entities**. Be aware of the following:

1. **Custom queries via fragment convention**: Complex queries beyond CRUD and derived methods are supported by implementing Spring Data custom fragments. Fragment implementations can opt-in to receive the repository-specific `FluentRepositoryContext<T>` (DSL + connection provider + row mapper + writer) via `FluentRepositoryContextAware<T>`. See the [Custom Query Fragments](#custom-query-fragments) section.

2. **No relationships**: The library does not load related entities automatically. If you need `User` with all their `Orders`, execute two separate queries and compose the result in application code.

3. **No persistence context**: Unlike Hibernate, entities are not tracked. Calling `findById()` twice returns two separate object instances. This is inherent to JDBC and not a limitation.

4. **ID generation limited**: Currently supports `PROVIDED` (app sets the ID) and `IDENTITY` (database auto-increment). `SEQUENCE` support is planned.

5. **Transaction management**: Relies entirely on Spring's `@Transactional` and `DataSourceUtils`. Manual connection management is not exposed to application code.

6. **No bulk operations**: `saveAll()` and `deleteAll()` execute in loops, not as batch statements. For high-volume inserts, consider batch APIs provided by the database driver directly.

7. **Explicit multi-datasource wiring**: When multiple `DataSource` beans exist, repository groups must be wired explicitly with `dataSourceRef` or advanced refs unless one candidate is marked `@Primary`.

---

## Building & Testing

### Building the Project

```bash
./mvnw clean install          # full build with tests
./mvnw clean install -DskipTests  # build without running tests
```

### Test Pyramid

Every test class that is not a plain unit test **must** carry the correct annotation. Do not rely solely on naming conventions.

|    Level    |     Annotation     |         Isolation         |         Database         | Speed  |
|-------------|--------------------|---------------------------|--------------------------|--------|
| Unit        | *(none)*           | Complete                  | No                       | Fast   |
| Component   | `@ComponentTest`   | Real classes, mocked JDBC | No (mocked)              | Fast   |
| Integration | `@IntegrationTest` | Real classes, embedded DB | H2                       | Medium |
| E2E         | `@E2ETest`         | Full system               | Testcontainers (real DB) | Slow   |

### Running Tests

|               Command                |                    What runs                     | Requires Docker |
|--------------------------------------|--------------------------------------------------|-----------------|
| `./mvnw test`                        | Unit + Component (fast, no database)             | No              |
| `./mvnw verify`                      | All tests (Unit + Component + Integration + E2E) | Yes (for E2E)   |
| `./mvnw test -Dgroups=component`     | Component tests only                             | No              |
| `./mvnw verify -Dgroups=integration` | Integration tests only (H2)                      | No              |
| `./mvnw verify -Dgroups=e2e`         | E2E tests only (Testcontainers)                  | Yes             |

**How it works:** Surefire (invoked by `./mvnw test`) excludes the `integration` and `e2e` tags, so only unit and component tests run in the fast path. Failsafe (invoked during `./mvnw verify`) picks up those same tags and runs integration and E2E tests against real or embedded databases.

For instructions on running tests and generating coverage reports, see [data/wiki/TEST.md](data/wiki/TEST.md).

### Code Formatting

Formatting is managed by the Spotless plugin:

```bash
./mvnw spotless:apply   # apply formatting
./mvnw spotless:check   # verify formatting without changes
```

A pre-commit hook that runs `spotless:apply` automatically can be installed with:

```bash
./mvnw process-resources
```

---

## Further Reading

For comprehensive architecture details and advanced usage examples, see:

- **[Architecture & Internal Components](data/wiki/ARCHITECTURE.md)** – Deep dive into Spring Data integration, connection management, and entity mapping machinery.
- **[Usage Examples](data/wiki/USAGE_EXAMPLES.md)** – Complete examples: UUID primary keys, Persistable<ID> implementation, transaction patterns, error handling.

---

## License

Apache License 2.0

This project is licensed under the Apache License, Version 2.0. See the LICENSE file for details.
