# Usage Examples

Concrete examples for common use cases. All examples assume H2 (test) or any standard JDBC database (production).

---

## 1. Application-Provided ID

The simplest strategy: the application sets the ID before calling `save()`.

```java
@Table(name = "users")
public class User {
    @Id
    private Long id;
    private String name;
    private String email;

    public User withId(long id) {
        return new User(id, this.name, this.email);
    }
}

public interface UserRepository extends CrudRepository<User, Long> {}
```

```java
// INSERT: id non-null, not in DB → INSERT_PROVIDED_ID
User user = new User("Alice", "alice@example.com").withId(1L);
repository.save(user);

// UPDATE: id non-null, exists in DB → UPDATE
user.setName("Alice Updated");
repository.save(user);
```

**Decision path**: id non-null → `existsById()` → INSERT or UPDATE.

---

## 2. Database Auto-Increment (IDENTITY)

The database generates the ID via `AUTO_INCREMENT` / `IDENTITY` / `SERIAL`.

```java
@Table(name = "cart_items")
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "unit_price")
    private Double unitPrice;

    @Column(name = "quantity")
    private Integer quantity;
}

public interface CartItemRepository extends CrudRepository<CartItem, Long> {}
```

```java
// INSERT: id == null → INSERT_AUTO_ID, library reads generated key and sets id on entity
CartItem item = new CartItem(null, "Coffee Cup", 9.99, 2);
CartItem saved = repository.save(item);
System.out.println(saved.getId()); // e.g. 1 — set by the library

// UPDATE: id non-null, exists in DB → UPDATE
saved.setQuantity(5);
repository.save(saved);
```

**Error case**: if you manually set an id on an IDENTITY entity and that id doesn't exist in the DB, `save()` throws `IllegalStateException`.

---

## 3. FluentPersistable for Explicit State Control

Implement `FluentPersistable<ID>` to control the new/existing decision yourself. The `SaveDecisionResolver` honours `isNew()` and **skips the `existsById()` DB call entirely**.

```java
import io.github.auspis.fluentrepo4j.FluentPersistable;

@Table(name = "products")
public class Product implements FluentPersistable<Integer> {
    @Id
    private Integer id;
    private String name;
    private Double price;
    private Integer quantity;

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

public interface ProductRepository extends CrudRepository<Product, Integer> {}
```

```java
// INSERT: isNew() = true → INSERT_PROVIDED_ID (no existsById call)
Product p = new Product(42, "Widget", 19.99, 100);
repository.save(p);

// UPDATE: after save/load, FluentPersistable.markPersisted() is called automatically
p.setPrice(24.99);
repository.save(p);  // isNew() = false → UPDATE (no existsById call)
```

> **Important**: `@PostLoad` / `@PostPersist` are JPA callbacks and are **not** triggered in pure JDBC mode.
> When the entity implements `FluentPersistable`, fluent-repo-4j calls `markPersisted()` automatically after saving and after loading from DB.

---

## 4. Persistable with UUID

`FluentPersistable<UUID>` is a natural fit for entities that generate their own UUID at construction time.

```java
import io.github.auspis.fluentrepo4j.FluentPersistable;

@Table(name = "orders")
public class Order implements FluentPersistable<UUID> {
    @Id
    private UUID id = UUID.randomUUID();  // always generated at construction

    private String description;

    @Transient
    private boolean isNewEntity = true;

    @Override
    public boolean isNew() { return isNewEntity; }

    @Override
    public void markPersisted() { this.isNewEntity = false; }
}
```

```java
Order order = new Order("New order");
repository.save(order);      // isNew() = true → INSERT

order.setDescription("Updated order");
repository.save(order);      // isNew() = false → UPDATE
```

---

## 5. CRUD Operations

```java
// Count
long total = repository.count();

// Find all
Iterable<User> all = repository.findAll();

// Find by id
Optional<User> found = repository.findById(1L);
found.ifPresent(u -> System.out.println(u.getName()));

// Exists
boolean exists = repository.existsById(1L);

// Delete by id
repository.deleteById(1L);

// Delete entity
User user = repository.findById(1L).orElseThrow();
repository.delete(user);

// Delete all
repository.deleteAll();

// Save multiple
List<User> users = List.of(
    new User("Alice", "a@example.com").withId(10L),
    new User("Bob", "b@example.com").withId(11L)
);
repository.saveAll(users);
```

---

## 6. Spring Transactions

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional
    public void placeOrder(Order order, List<CartItem> items) {
        // Both operations share the same JDBC connection / transaction
        Order saved = orderRepository.save(order);
        items.forEach(cartItemRepository::save);
        // On exception → automatic rollback
    }

    @Transactional(readOnly = true)
    public Optional<Order> findOrder(UUID id) {
        return orderRepository.findById(id);
    }
}
```

---

## 7. Convention-Based Column Mapping

Fields without `@Column` are automatically mapped to `snake_case` column names.

```java
@Table(name = "user_profiles")
public class UserProfile {
    @Id
    private Long id;

    private String firstName;    // → column "first_name"
    private String lastName;     // → column "last_name"
    private Integer postalCode;  // → column "postal_code"

    @Column(name = "email_address")
    private String email;        // → column "email_address" (explicit)

    @Transient
    private String computed;     // → excluded from all SQL
}
```

---

## 8. OptimisticLockingFailureException on Update

When `update()` is called but the row has been deleted between the `existsById()` check and the actual UPDATE, `save()` throws `OptimisticLockingFailureException`.

```java
try {
    repository.save(entity);
} catch (OptimisticLockingFailureException e) {
    // Entity was deleted by another process between check and update
    // Retry or report error
}
```

This applies to:
- Standard entities: id non-null, existsById returned true, but row gone before UPDATE
- `Persistable` entities: `isNew()` = false, but row doesn't exist

---

## 9. Multi-DataSource Repository Groups

When the application has more than one `DataSource`, configure one `@EnableFluentRepositories` block per repository group.

### Scenario A. Repository Group Bound by `dataSourceRef`

Use this when you want the library to derive `FluentConnectionProvider` and `DSL` from a specific `DataSource`.

```java
@Configuration(proxyBeanMethods = false)
@EnableFluentRepositories(
        basePackages = "com.example.billing",
        dataSourceRef = "billingDataSource",
        transactionManagerRef = "billingTransactionManager")
class BillingRepositoryConfiguration {

    @Bean
    DataSource billingDataSource() {
        return ...;
    }

    @Bean
    PlatformTransactionManager billingTransactionManager(DataSource billingDataSource) {
        return new DataSourceTransactionManager(billingDataSource);
    }
}
```

What happens:

- Repositories in `com.example.billing` use `billingDataSource`
- fluent-repo-4j creates a `FluentConnectionProvider` on demand for that repository group
- fluent-repo-4j auto-detects the SQL dialect using the configured `DSLRegistry`

### Scenario B. Repository Group Bound by Explicit `connectionProviderRef` and `dslRef`

Use this when you want complete control over the infrastructure beans.

```java
@Configuration(proxyBeanMethods = false)
@EnableFluentRepositories(
        basePackages = "com.example.reporting",
        connectionProviderRef = "reportingConnectionProvider",
        dslRef = "reportingDsl",
        transactionManagerRef = "reportingTransactionManager")
class ReportingRepositoryConfiguration {

    @Bean
    DataSource reportingDataSource() {
        return ...;
    }

    @Bean
    FluentConnectionProvider reportingConnectionProvider(DataSource reportingDataSource) {
        return new FluentConnectionProvider(reportingDataSource);
    }

    @Bean
    DSL reportingDsl(DataSource reportingDataSource, DSLRegistry registry) {
        return DialectDetector.detect(reportingDataSource, registry);
    }

    @Bean
    PlatformTransactionManager reportingTransactionManager(DataSource reportingDataSource) {
        return new DataSourceTransactionManager(reportingDataSource);
    }
}
```

What happens:

- Repositories in `com.example.reporting` use the exact beans you provide
- No infrastructure is derived automatically for that group
- This is the most explicit and least ambiguous setup

### Scenario C. Multiple `DataSource` Beans with One `@Primary`

Use this when one datasource is the default and only some repository groups need explicit overrides.

```java
@Bean
@Primary
DataSource mainDataSource() {
    return ...;
}

@Bean
DataSource auditDataSource() {
    return ...;
}

@Configuration(proxyBeanMethods = false)
@EnableFluentRepositories(basePackages = "com.example.main")
class MainRepositoryConfiguration {
}
```

What happens:

- Repository groups without explicit refs bind to the primary datasource
- Repository groups with `dataSourceRef` or explicit refs override that default

### Scenario D. Ambiguous Setup Without Refs and Without `@Primary`

This configuration is rejected at startup:

```java
@Bean
DataSource firstDataSource() {
    return ...;
}

@Bean
DataSource secondDataSource() {
    return ...;
}

@Configuration(proxyBeanMethods = false)
@EnableFluentRepositories(basePackages = "com.example.repositories")
class RepositoryConfiguration {
}
```

Expected result:

- Startup fails fast
- The error tells you to use `dataSourceRef` or mark one datasource as `@Primary`

### Decision Matrix

|                 Scenario                 |                      What the user configures                       |                            Required beans                             |          Repository binding behavior           |
|------------------------------------------|---------------------------------------------------------------------|-----------------------------------------------------------------------|------------------------------------------------|
| Single datasource                        | Nothing extra                                                       | One `DataSource`                                                      | Uses the single candidate                      |
| Multiple datasources with default        | One `@Primary` datasource                                           | Multiple `DataSource` beans, one `@Primary`                           | Uses the primary datasource when no ref is set |
| Explicit datasource per repository group | `dataSourceRef`, optional `dslRegistryRef`, `transactionManagerRef` | Named `DataSource`, optional named `DSLRegistry`, transaction manager | Derives provider and DSL from that datasource  |
| Full manual infrastructure               | `connectionProviderRef`, `dslRef`, `transactionManagerRef`          | Named `FluentConnectionProvider`, named `DSL`, transaction manager    | Uses the exact infrastructure beans provided   |
| Ambiguous multi-datasource               | Nothing extra                                                       | Two or more `DataSource` beans without `@Primary`                     | Fails fast with configuration error            |

---

## 10. Integration Test Setup (H2)

```java
@Nested
class UserRepositoryIT {

    private Connection connection;
    private FluentRepository<User, Long> repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = TestDatabaseUtil.createH2Connection();
        TestDatabaseUtil.createUsersTable(connection);

        DataSource dataSource = new SingleConnectionDataSource(connection, true);
        DSL dsl = DialectDetector.detect(dataSource, DSLRegistry.createWithServiceLoader());
        FluentConnectionProvider connectionProvider = new FluentConnectionProvider(dataSource);
        FluentEntityInformation<User, Long> entityInfo = new FluentEntityInformation<>(User.class);

        repository = new FluentRepository<>(entityInfo, connectionProvider, dsl);
    }

    @AfterEach
    void tearDown() throws SQLException {
        TestDatabaseUtil.closeConnection(connection);
    }

    @Test
    void save_insert() {
        User user = new User("Alice", "alice@example.com").withId(1L);
        repository.save(user);
        assertThat(repository.findById(1L)).isPresent();
    }
}
```

