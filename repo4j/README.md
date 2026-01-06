# repo4j - JDBC Repository Pattern

An enterprise-grade library for implementing the **Repository Pattern** with pure JDBC, maintaining a clean business-focused API decoupled from the database.

## Features

✅ **Zero Spring dependencies** - Completely standalone base  
✅ **Pure JDBC** - No ORM complexity, full control  
✅ **Flexible Connection Management** - ThreadLocal or ScopedValue (Java 21+)  
✅ **Generic BaseRepository** - Inherit once, implement specific queries  
✅ **JUnit 6 Ready** - Complete tests with H2 in-memory  
✅ **Enterprise Design** - Decoupling, Exception handling, RowMapper pattern  
✅ **Virtual Threads Ready** - ScopedValue support for modern concurrency  

---

## Quick Start

### 1. Create an entity

```java
public class User {
    private Long id;
    private String name;
    private String email;
    
    // Constructors, getters, setters...
}
```

### 2. Create a database schema

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL
);
```

### 3. Implement the Repository

```java
import io.github.auspis.repo4j.core.provider.ConnectionProvider;
import io.github.auspis.repo4j.core.provider.ConnectionProviderFactory;

public class UserRepository extends BaseRepository<User, Long> {
    
    // Constructor accepting a ConnectionProvider
    public UserRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }
    
    // RowMapper to map ResultSet → User
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

    // Query specifiche del dominio (bonus!)
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT id, name, email FROM users WHERE email = ?";
        return executeQuerySingle(sql, USER_MAPPER, email);
    }
}
```

### 4. Use the Repository

#### Option A: ThreadLocal (Traditional approach)

```java
import io.github.auspis.repo4j.core.provider.ConnectionProviderFactory;

public static void main(String[] args) throws Exception {
    // 1. Create a connection
    Connection conn = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");
    
    // 2. Create a ThreadLocal provider
    ConnectionProvider provider = ConnectionProviderFactory.threadLocal();
    provider.setConnection(conn);
    
    try {
        // 3. Use the repository - ZERO Connection in method signatures!
        UserRepository repo = new UserRepository(provider);
        
        User newUser = repo.create(new User("John Doe", "john@example.com"));
        System.out.println("Created: " + newUser);
        
        Optional<User> found = repo.findById(newUser.getId());
        System.out.println("Found: " + found);
        
        newUser.setName("Jane Doe");
        repo.update(newUser);
        
        List<User> all = repo.findAll();
        System.out.println("All users: " + all);
        
    } finally {
        // 4. Clean up the connection
        provider.close();
    }
}
```

#### Option B: ScopedValue (Java 21+, Virtual Threads)

```java
import io.github.auspis.repo4j.core.provider.ConnectionProviderFactory;
import io.github.auspis.repo4j.core.provider.ScopedValueConnectionProvider;

public static void main(String[] args) throws Exception {
    // 1. Create a connection
    Connection conn = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");
    
    // 2. Create a ScopedValue provider
    ScopedValueConnectionProvider provider = 
        (ScopedValueConnectionProvider) ConnectionProviderFactory.scopedValue();
    
    // 3. Execute within a scoped context
    provider.executeInScope(conn, () -> {
        UserRepository repo = new UserRepository(provider);
        
        User newUser = repo.create(new User("John Doe", "john@example.com"));
        System.out.println("Created: " + newUser);
        
        List<User> all = repo.findAll();
        System.out.println("All users: " + all);
    });
    
    // Connection automatically unbound after scope exits
    conn.close();
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

MIT
