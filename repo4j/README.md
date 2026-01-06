# repo4j - JDBC Repository Pattern

Una libreria enterprise-grade per implementare il **Repository Pattern** con JDBC puro, mantenendo un'API pulita e business-focused disaccoppiata dal database.

## Caratteristiche

✅ **Zero dipendenze Spring** - Base completamente standalone  
✅ **JDBC Puro** - Niente ORM complexity, pieno controllo  
✅ **ThreadLocal Connection Management** - La connessione non appare mai in firma ai metodi  
✅ **Generico BaseRepository** - Eredita una volta, implementa query specifiche  
✅ **JUnit 6 Ready** - Test completi con H2 in-memory  
✅ **Enterprise Design** - Disaccoppiamento, Exception handling, RowMapper pattern  

---

## Quick Start

### 1. Creare un'entità

```java
public class User {
    private Long id;
    private String name;
    private String email;
    
    // Constructors, getters, setters...
}
```

### 2. Creare uno schema nel database

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL
);
```

### 3. Implementare il Repository

```java
public class UserRepository extends BaseRepository<User, Long> {
    
    // RowMapper per mappare ResultSet → User
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

### 4. Usare il Repository

```java
public static void main(String[] args) throws Exception {
    // 1. Crea una connessione
    Connection conn = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");
    
    // 2. Imposta nel provider (ThreadLocal)
    ConnectionProvider.setConnection(conn);
    
    try {
        // 3. Usa il repository - ZERO Connection in firma!
        UserRepository repo = new UserRepository();
        
        User newUser = repo.create(new User("John Doe", "john@example.com"));
        System.out.println("Created: " + newUser);
        
        Optional<User> found = repo.findById(newUser.getId());
        System.out.println("Found: " + found);
        
        newUser.setName("Jane Doe");
        repo.update(newUser);
        
        List<User> all = repo.findAll();
        System.out.println("All users: " + all);
        
    } finally {
        // 4. Pulisci la connessione
        ConnectionProvider.close();
    }
}
```

---

## Architecture

### ConnectionProvider (ThreadLocal Wrapper)

Gestisce il ciclo di vita della `Connection` per thread senza esporla ai repository.

```java
// Imposta la Connection per il thread corrente
ConnectionProvider.setConnection(connection);

// Leggi da qualsiasi metodo del repository
Connection conn = ConnectionProvider.getConnection();

// Pulisci alla fine
ConnectionProvider.close();  // chiude la connection
ConnectionProvider.clear();  // solo rimuove dal ThreadLocal
```

**Vantaggi ThreadLocal:**
- ✅ Una sola Connection per thread (thread-safe per applicazioni multi-threaded)
- ✅ Repository non sa della Connection, completamente disaccoppiato
- ✅ Naturale integrazione con Spring `@Transactional` (Spring popola il ThreadLocal automaticamente)

### BaseRepository<T, ID>

Classe astratta con utility methods per CRUD common:

| Metodo | Descrizione |
|--------|-------------|
| `create(T)` | INSERT - da implementare |
| `findById(ID)` | SELECT by ID - da implementare |
| `findAll()` | SELECT all - da implementare |
| `update(T)` | UPDATE - da implementare |
| `delete(ID)` | DELETE - da implementare |
| `executeQuery(sql, mapper, params)` | Esegui SELECT, ritorna List |
| `executeQuerySingle(sql, mapper, params)` | Esegui SELECT, ritorna Optional |
| `executeUpdate(sql, params)` | Esegui INSERT/UPDATE/DELETE |
| `executeInsertWithGeneratedKey(sql, mapper, params)` | INSERT con PRIMARY KEY generata |

### RowMapper<T>

Interfaccia funzionale per mappare ResultSet → T:

```java
@FunctionalInterface
public interface RowMapper<T> {
    T mapRow(ResultSet rs) throws SQLException;
}

// Usa come lambda:
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
