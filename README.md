# repo4j - JDBC Repository Pattern

An enterprise-grade library for implementing the **Repository Pattern** with pure JDBC, maintaining a clean business-focused API decoupled from the database.

## Features

✅ **Minimal Spring dependencies** - Uses Spring Data Commons and spring-jdbc  
✅ **Pure JDBC** - No ORM complexity, full control  
✅ **Flexible Connection Management** - ThreadLocal or ScopedValue (Java 21+)  
✅ **Generic BaseRepository** - Inherit once, implement specific queries  
✅ **JUnit 5 (Jupiter)** - Test suite with H2 in-memory database  
✅ **Enterprise Design** - Decoupling, Exception handling, RowMapper pattern  
✅ **Virtual Threads Ready** - ScopedValue support for modern concurrency  

---

## Quick Start

### With Spring Boot (Recommended)

Include this library as a dependency. Spring Boot auto-configures everything!

```xml
<dependency>
    <groupId>io.github.auspis</groupId>
    <artifactId>fluent-repo-4j</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### 1. Define your entity with JPA annotations

```java
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Id;

@Table(name = "users")
public class User {
    @Id
    private Long id;
    
    @Column(name = "name")
    private String name;
    
    @Column(name = "email")
    private String email;
    
    // Constructors, getters, setters...
}
```

#### 2. Create a repository interface

```java
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {
    // CRUD methods are inherited: save, findById, findAll, deleteById, count, etc.
    // Add custom queries if needed
    Optional<User> findByEmail(String email);
}
```

#### 3. Use it in a service

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Transactional
    public User createUser(String name, String email) {
        // Spring Data proxy automatically:
        // 1. Gets a connection from the DataSource
        // 2. Uses FluentConnectionProvider to bind it to transaction
        // 3. Delegates to SimpleFluentRepository.save()
        User user = new User(name, email);
        return userRepository.save(user);
    }
    
    public User getUser(Long id) {
        return userRepository.findById(id).orElseThrow();
    }
    
    public List<User> getAllUsers() {
        return (List<User>) userRepository.findAll();
    }
    
    @Transactional
    public void updateUser(Long id, String newName, String newEmail) {
        User user = userRepository.findById(id).orElseThrow();
        user.setName(newName);
        user.setEmail(newEmail);
        userRepository.save(user);  // UPDATE
    }
    
    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
```

#### 4. Use the service in a controller (or anywhere)

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @PostMapping
    public User create(@RequestParam String name, @RequestParam String email) {
        return userService.createUser(name, email);
    }
    
    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userService.getUser(id);
    }
    
    @GetMapping
    public List<User> getAll() {
        return userService.getAllUsers();
    }
    
    @PutMapping("/{id}")
    public User update(@PathVariable Long id, 
                       @RequestParam String name, 
                       @RequestParam String email) {
        userService.updateUser(id, name, email);
        return userService.getUser(id);
    }
    
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userService.deleteUser(id);
    }
}
```

#### 5. Configure the datasource (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/myapp
    username: root
    password: secret
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    database-platform: org.hibernate.dialect.MySQL8Dialect
```

No additional configuration needed! The library:
- ✅ Auto-detects the database dialect from DataSource metadata
- ✅ Scans for `CrudRepository` interfaces automatically
- ✅ Creates beans with transaction support via `@Transactional`
- ✅ Manages connections via `DataSourceUtils` (integrated with Spring Transactions)

---

### Without Spring Boot (Standalone)

The library also works as a plain JDBC wrapper without Spring:

```java
public class User {
    private Long id;
    private String name;
    private String email;
    // ...
}

public class UserRepository extends BaseRepository<User, Long> {
    
    public UserRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }
    
    private static final RowMapper<User> USER_MAPPER = rs -> 
        new User(rs.getLong("id"), rs.getString("name"), rs.getString("email"));

    @Override
    public User create(User user) {
        String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
        Long generatedId = executeInsertWithGeneratedKey(
            sql, 
            rs -> rs.getLong(1), 
            user.getName(), 
            user.getEmail()
        );
        user.setId(generatedId);
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        String sql = "SELECT id, name, email FROM users WHERE id = ?";
        return executeQuerySingle(sql, USER_MAPPER, id);
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT id, name, email FROM users ORDER BY id";
        return executeQuery(sql, USER_MAPPER);
    }

    @Override
    public User update(User user) {
        String sql = "UPDATE users SET name = ?, email = ? WHERE id = ?";
        executeUpdate(sql, user.getName(), user.getEmail(), user.getId());
        return user;
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        executeUpdate(sql, id);
    }
}
```

---

## Architecture

### ConnectionProvider Implementations

The library provides two connection management strategies following the **Single Responsibility Principle (SRP)**:

#### 1. ThreadLocalConnectionProvider
- Uses `ThreadLocal` for connection storage
- Suitable for traditional thread pool scenarios
- Compatible with all Java versions
- Each instance has its own isolated ThreadLocal storage

#### 2. ScopedValueConnectionProvider  
- Uses `ScopedValue` (Java 21+) for connection storage
- Optimized for virtual threads and structured concurrency
- Better garbage collection performance
- Automatic scope binding/unbinding

### Factory Pattern

```java
// Create providers using the factory
ConnectionProvider threadLocalProvider = ConnectionProviderFactory.threadLocal();
ConnectionProvider scopedProvider = ConnectionProviderFactory.scopedValue();

// Each call returns a fresh instance with isolated state
```

### ConnectionProvider Interface

Manages the `Connection` lifecycle without exposing it to repositories.

```java
public interface ConnectionProvider {
    void setConnection(Connection connection);
    Connection getConnection();
    boolean hasConnection();
    void clear();  // Remove without closing
    void close();  // Close and remove
}
```

### When to Use Which Provider?

#### Use **ThreadLocalConnectionProvider** when:
- ✅ Working with traditional thread pools (e.g., Servlet containers)
- ✅ Need compatibility with older Java versions (pre-21)
- ✅ Using Spring Framework with `@Transactional` (Spring manages ThreadLocal automatically)
- ✅ Standard enterprise applications with fixed thread pools

#### Use **ScopedValueConnectionProvider** when:
- ✅ Working with virtual threads (Java 21+)
- ✅ Need better garbage collection performance
- ✅ Implementing structured concurrency patterns
- ✅ Want explicit scope-based lifecycle management
- ✅ Building modern reactive/async applications

### Key Advantages

**Architecture Benefits:**
- ✅ One Connection per thread/scope (thread-safe for multi-threaded applications)
- ✅ Repository completely decoupled from Connection management
- ✅ Clean separation of concerns (SRP applied)
- ✅ Easy testing with dependency injection
- ✅ No Connection parameters in method signatures

**ScopedValue Benefits (Java 21+):**
- ✅ Better performance with virtual threads
- ✅ Reduced memory footprint
- ✅ Automatic scope cleanup
- ✅ Immutable binding (safer than ThreadLocal)

### BaseRepository<T, ID>

Abstract class with utility methods for common CRUD operations:

| Method | Description |
|--------|-------------|
| `create(T)` | INSERT - to be implemented |
| `findById(ID)` | SELECT by ID - to be implemented |
| `findAll()` | SELECT all - to be implemented |
| `update(T)` | UPDATE - to be implemented |
| `delete(ID)` | DELETE - to be implemented |
| `executeQuery(sql, mapper, params)` | Execute SELECT, return List |
| `executeQuerySingle(sql, mapper, params)` | Execute SELECT, return Optional |
| `executeUpdate(sql, params)` | Execute INSERT/UPDATE/DELETE |
| `executeInsertWithGeneratedKey(sql, mapper, params)` | INSERT with generated PRIMARY KEY |

### RowMapper<T>

Functional interface for mapping ResultSet → T:

```java
@FunctionalInterface
public interface RowMapper<T> {
    T mapRow(ResultSet rs) throws SQLException;
}

// Use as lambda:
RowMapper<User> userMapper = rs -> new User(
    rs.getLong("id"),
    rs.getString("name"),
    rs.getString("email")
);
```

### RepositoryException

Eccezione unchecked che wrappa `SQLException`:

```java
try {
    // JDBC operation
} catch (SQLException e) {
    throw new RepositoryException("Errore durante l'operazione", e);
}
```

---

## Testing

Ogni repository test usa H2 in-memory database con ThreadLocal isolation:

```java
@BeforeEach
void setUp() throws Exception {
    // Crea DB in-memory con timestamp unico per test isolation
    Connection conn = DriverManager.getConnection(
        "jdbc:h2:mem:test_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1", 
        "sa", 
        ""
    );
    
    // Schema DDL
    try (Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("CREATE TABLE users (...)");
    }
    
    // Imposta provider
    ConnectionProvider.setConnection(conn);
    repository = new UserRepository();
}

@Test
void testCreate() {
    User created = repository.create(new User("Test", "test@example.com"));
    assertEquals("Test", created.getName());
}

@AfterEach
void tearDown() {
    ConnectionProvider.close();
}
```

---

## Integration con Spring (Futuro)

Una volta integrato Spring, **la base rimarrà identica**, solo il codice client cambierà:

```java
// Senza Spring (attuale)
Connection conn = dataSource.getConnection();
ConnectionProvider.setConnection(conn);
try {
    userRepo.create(user);
} finally {
    ConnectionProvider.close();
}

// Con Spring (futuro)
@Transactional  // ← Spring gestisce ConnectionProvider automaticamente
public void createUser(User user) {
    userRepo.create(user);  // ← IDENTICO!
}
```

I **repository non cambiano una riga**.

---

## Project Structure
// TODO: align Project Structure (the whole README.md needs to be aligned)
```
src/
├── main/java/io/github/auspis/repo4j/
│   ├── core/
│   │   ├── ConnectionProvider.java      (ThreadLocal wrapper)
│   │   ├── BaseRepository.java          (Classe astratta generica)
│   │   ├── RowMapper.java               (Interface mapping)
│   │   └── RepositoryException.java     (Unchecked exception)
│   └── example/
│       ├── User.java                    (Entità di esempio)
│       └── UserRepository.java          (Repository concreto)
└── test/java/io/github/auspis/repo4j/
    ├── core/
    │   └── ConnectionProviderTest.java
    └── example/
        └── UserRepositoryTest.java
```

---

## Spring Data Integration

This module integrates with **Spring Data Commons** to provide automatic repository scanning and bean creation. When you include this library in a Spring Boot application, it automatically configures repository support with zero boilerplate.

### Component Responsibilities

| Component | Responsibility |
|-----------|-----------------|
| **`@EnableFluentRepositories`** | Entry-point annotation; imports the registrar for repository scanning |
| **`FluentRepositoriesRegistrar`** | Implements Spring's `ImportBeanDefinitionRegistrar`; triggers classpath scanning for `Repository` interfaces |
| **`FluentRepositoryConfigExtension`** | Provides configuration strategy to Spring Data: which factory to use, how to identify repositories |
| **`FluentRepositoryFactoryBean`** | Factory bean that creates `FluentRepositoryFactory` and injects `FluentConnectionProvider` + `DSL` |
| **`FluentRepositoryFactory`** | Creates `SimpleFluentRepository<T, ID>` instances; extracts entity metadata |
| **`SimpleFluentRepository<T, ID>`** | **Actual implementation**: executes `findById`, `save`, `delete`, etc. using JDBC + fluent-sql-4j |
| **`FluentConnectionProvider`** | Obtains connections via Spring's `DataSourceUtils` (transaction-aware or auto-commit) |
| **`FluentEntityInformation<T, ID>`** | Extracts entity metadata: table name, column names, `@Id` field using Jakarta Persistence annotations |
| **`FluentEntityRowMapper<T>`** | Maps `ResultSet` rows to entity instances |
| **`FluentEntityWriter<T>`** | Writes entity properties to `PreparedStatement` parameters |

### Configuration Flow

```
Spring Boot Application starts
        ↓
@EnableFluentRepositories detected (or auto-configured)
        ↓
FluentRepositoriesRegistrar scans basePackages
        ↓
Finds interfaces extending CrudRepository (e.g., UserRepository)
        ↓
Creates BeanDefinition pointing to FluentRepositoryFactoryBean
        ↓
When @Autowired UserRepository is encountered:
    1. FluentRepositoryFactoryBean.getObject() invoked
    2. FluentRepositoryFactory created (receives ConnectionProvider + DSL)
    3. SimpleFluentRepository<User, Long> instantiated
    4. Spring Data creates proxy around implementation
    5. Proxy injected into application bean
        ↓
userRepository.findById(1L) 
    → Proxy delegates to SimpleFluentRepository.findById()
    → Executes SQL via FluentConnectionProvider
```

---

## Stack

- **Java**: 21+
- **Build**: Maven
- **Test**: JUnit 6 (Jupiter)
- **Database**: H2 (dev/test), any JDBC driver (production)
- **Dependencies**: Zero (JDBC driver è l'unico requirement)

---

## Best Practices

1. **Un repository per entità** - UserRepository per User, ProductRepository per Product, ecc.

2. **Query specifiche nel repository** - Domain queries vivono accanto al mapping logic

3. **Usa Optional** - Per single-row queries, List per multi-row

4. **Thread safety** - ConnectionProvider è thread-safe tramite ThreadLocal

5. **Sempre chiudi la Connection** - Use try/finally o try-with-resources

6. **Mock ConnectionProvider nei test** - Non necesità di database reale

---

## License

Apache License 2.0

This project is licensed under the Apache License, Version 2.0. See the LICENSE file for details.
