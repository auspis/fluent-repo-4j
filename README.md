# fluent-repo-4j - Pure JDBC Repositories for Spring Boot

A lightweight Spring Boot library for implementing the **Repository Pattern** with pure JDBC and the fluent-sql-4j DSL. Write type-safe, declarative database queries without ORM overhead.

## Features

✅ **Spring Boot Auto-Configuration** - Zero boilerplate; `@EnableFluentRepositories` scans and creates repository beans  
✅ **Pure JDBC** - Full SQL control via fluent-sql-4j DSL; no ORM complexity  
✅ **Spring Transaction Integration** - Automatic binding via `DataSourceUtils`; `@Transactional` works seamlessly  
✅ **Simple Entity Mapping** - Jakarta Persistence annotations (`@Table`, `@Column`, `@Id`); automatic snake_case conversion  
✅ **ID Generation Strategies** - Support for application-provided IDs and database auto-increment (`@GeneratedValue(IDENTITY)`)  
✅ **Type Conversion** - Automatic mapping: strings, numbers, booleans, dates (LocalDate, LocalDateTime)  
✅ **Exception Translation** - SQL exceptions automatically translated to Spring's `DataAccessException`  

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

```java
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {
    // Inherited methods: save(), findById(), findAll(), count(), deleteById(), etc.
}
```

### 3. Inject and Use

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

### 4. Configure DataSource

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

---

## ID Generation Strategies

How does the library decide whether to INSERT or UPDATE? It uses the `isNew()` method, which inspects the ID and optional `@GeneratedValue` annotation.

### Strategy 1: Application-Provided ID (Default)

The **application** is responsible for setting the ID before calling `save()`.

```java
@Table(name = "events")
public class Event {
    @Id
    private UUID id;  // No @GeneratedValue annotation
    
    private String description;
    
    // Constructor, getters, setters...
}

// Usage:
Event event = new Event(UUID.randomUUID(), "Something happened");
repository.save(event);  // ID is set → INSERT
```

**Behavior**: If ID is not null, `isNew()` returns false → INSERT is executed (not UPDATE).  
**Best for**: UUIDs, business keys, or scenarios where the app generates the ID.

### Strategy 2: Database Auto-Increment

Let the **database** generate the ID using auto-increment / identity columns.

```java
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Database generates this
    
    private String name;
    
    // Constructor, getters, setters...
}

// Usage:
User user = new User("Alice");  // id = null
repository.save(user);  // ID is null → INSERT without ID, database returns generated key
```

**Behavior**: When ID is null, `isNew()` returns true → INSERT is executed. The library retrieves the generated ID from the database and populates the entity.  
**Best for**: Sequential IDs (IDENTITY, SERIAL, AUTO_INCREMENT columns).

### Strategy 3: Custom isNew() Logic via Persistable<ID>

For complex scenarios (e.g., UUID with explicit `isNew` tracking), implement `Persistable<ID>`.

```java
import org.springframework.data.domain.Persistable;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;

@Table(name = "orders")
public class Order implements Persistable<UUID> {
    @Id
    private UUID id;
    
    @Transient
    private boolean isNew = true;
    
    private String description;
    
    public Order() {
        this.id = UUID.randomUUID();  // Generate UUID at construction
    }
    
    @Override
    public UUID getId() {
        return id;
    }
    
    @Override
    public boolean isNew() {
        return isNew;  // You control when the entity is considered "new"
    }
    
    @PostLoad
    @PostPersist
    void markPersisted() {
        this.isNew = false;
    }
}

// Usage:
Order order = new Order();  // isNew = true, UUID already generated
repository.save(order);  // isNew() → true → INSERT
```

**Behavior**: `save()` calls `isNew()` directly on the entity. Full control over insert/update logic.  
**Best for**: Complex ID schemes or when you need explicit state tracking.

---

## Data Types Supported

The library automatically converts ResultSet columns to Java types:

| Java Type | Supported | Notes |
|-----------|-----------|-------|
| String | ✅ | VARCHAR, TEXT, CHAR |
| Long, Integer, Short, Byte | ✅ | BIGINT, INT, SMALLINT, TINYINT |
| Double, Float, BigDecimal | ✅ | DOUBLE, FLOAT, DECIMAL |
| Boolean | ✅ | BOOLEAN, BIT (0/1 converts to false/true) |
| LocalDate | ✅ | DATE |
| LocalDateTime | ✅ | TIMESTAMP |
| UUID | ✅ | VARCHAR/CHAR (stored as string) |
| Custom types, arrays, LOBs | ❌ | Not supported; implement custom converters or store as JSON strings |

---

## Architecture Snapshot

How does the library work?

1. **Application startup**: Spring Boot detects `@EnableFluentRepositories` (or auto-configuration) and triggers repository scanning.
2. **Repository bean creation**: For each `CrudRepository` interface, a `FluentRepositoryFactoryBean` creates a `SimpleFluentRepository<Entity, ID>` implementation and wraps it with Spring Data's proxy.
3. **Method invocation**: When you call `repository.save(entity)`, the proxy invokes `SimpleFluentRepository.save()`.
4. **Save logic**:
   - Check `entity.isNew()` (uses `@GeneratedValue` annotation or `Persistable<ID>` override)
   - If true → INSERT; if false → UPDATE
5. **SQL execution**: `SimpleFluentRepository` builds SQL using fluent-sql-4j, prepares statements via `FluentConnectionProvider`, and executes against the DataSource.
6. **Transaction binding**: Connections are obtained via Spring's `DataSourceUtils`, automatically bound to the active `@Transactional` scope.
7. **Results mapping**: `FluentEntityRowMapper` converts ResultSet rows to entity instances using Jakarta Persistence metadata.

**For detailed component descriptions and architecture diagrams**, see [data/wiki/ARCHITECTURE.md](data/wiki/ARCHITECTURE.md).

---

## Supported vs Not Supported

| Feature | Status | Notes |
|---------|--------|-------|
| CRUD operations (`save`, `findById`, `findAll`, `count`, `deleteById`) | ✅ Supported | Core functionality built-in |
| `@Transactional` integration | ✅ Supported | Automatic connection binding via Spring |
| `@GeneratedValue(IDENTITY)` | ✅ Supported | Database auto-increment IDs |
| Application-provided IDs | ✅ Supported | Set ID before `save()` |
| `Persistable<ID>` for custom `isNew()` logic | ✅ Supported | Fine-grained control over insert/update |
| Simple entity mapping (Jakarta Persistence annotations) | ✅ Supported | `@Table`, `@Column`, `@Id`, `@GeneratedValue`, `@Transient` |
| Exception translation to `DataAccessException` | ✅ Supported | Automatic SQL exception handling |
| Custom query methods (e.g., `findByEmail()`) | ❌ Not Supported | Use `findAll()` + filter in application code, or implement custom SQL in fragments |
| Query method derivation (e.g., PartTree) | ❌ Not Supported | Planned for future release |
| Object relationships (one-to-many, many-to-many) | ❌ Not Supported | Use separate repositories and explicit queries |
| `@GeneratedValue(SEQUENCE)` | ❌ Not Supported | Planned for future release |
| Persistence context / first-level cache | ❌ Not Supported | Not applicable to JDBC; each query returns fresh objects |

---

## Current Limitations

This library focuses on **simple CRUD operations for single entities**. Be aware of the following:

1. **CRUD-only API**: No custom query methods. Use `findAll()` + application-side filtering, or implement custom SQL using fluent-sql-4j directly.

2. **No relationships**: The library does not load related entities automatically. If you need `User` with all their `Orders`, execute two separate queries and compose the result in application code.

3. **No persistence context**: Unlike Hibernate, entities are not tracked. Calling `findById()` twice returns two separate object instances. This is inherent to JDBC and not a limitation.

4. **ID generation limited**: Currently supports `PROVIDED` (app sets the ID) and `IDENTITY` (database auto-increment). `SEQUENCE` support is planned.

5. **Transaction management**: Relies entirely on Spring's `@Transactional` and `DataSourceUtils`. Manual connection management is not exposed to application code.

6. **No bulk operations**: `saveAll()` and `deleteAll()` execute in loops, not as batch statements. For high-volume inserts, consider batch APIs provided by the database driver directly.

---

## Further Reading

For comprehensive architecture details and advanced usage examples, see:

- **[Architecture & Internal Components](data/wiki/ARCHITECTURE.md)** – Deep dive into Spring Data integration, connection management, and entity mapping machinery.
- **[Usage Examples](data/wiki/USAGE_EXAMPLES.md)** – Complete examples: UUID primary keys, Persistable<ID> implementation, transaction patterns, error handling.

---

## License

Apache License 2.0

This project is licensed under the Apache License, Version 2.0. See the LICENSE file for details.
