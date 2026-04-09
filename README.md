<p align="center">
  <img src="data/wiki/assets/fluent-repo-4j-logo-no-bg.png" alt="Fluent Repo 4J" width="350">
</p>

[![Try the demo](https://img.shields.io/badge/Try%20the%20Demo-Run%20Locally-brightgreen?style=flat-square&logo=github)](https://github.com/auspis/fluent-repo-4j-demo)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=auspis_fluent-repo-4j&metric=alert_status&token=ea0f4fd35e38159d168f6ad1f6e4f7a128748650)](https://sonarcloud.io/summary/new_code?id=auspis_fluent-repo-4j)
[![Reliability](https://sonarcloud.io/api/project_badges/measure?project=auspis_fluent-repo-4j&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=auspis_fluent-repo-4j)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=auspis_fluent-repo-4j&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=auspis_fluent-repo-4j)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.auspis/fluent-repo-4j)](https://central.sonatype.com/artifact/io.github.auspis/fluent-repo-4j)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/auspis/fluent-repo-4j/blob/main/LICENSE)

[![CI](https://github.com/auspis/fluent-repo-4j/actions/workflows/ci.yml/badge.svg)](https://github.com/auspis/fluent-repo-4j/actions?query=workflow%3ACI)
[![Release](https://github.com/auspis/fluent-repo-4j/actions/workflows/release.yml/badge.svg)](https://github.com/auspis/fluent-repo-4j/actions?query=workflow%3A"Release+to+Maven+Central")

A lightweight Spring Boot library for the **Repository Pattern** with pure JDBC and the [fluent-sql-4j](https://github.com/auspis/fluent-sql-4j) DSL.
Write type-safe, declarative database queries without ORM overhead.

> ✨ **New from version 1.2.0:** Functional Repositories — `RepositoryResult<T>` return types make success and failure explicit at the call site, replacing unchecked exceptions with composable values, see [FUNCTIONAL_REPOSITORY documentation](data/wiki/FUNCTIONAL_REPOSITORY.md).

## Features

✅ **Spring Boot Auto-Configuration** — Zero boilerplate; `@EnableFluentRepositories` scans and creates repository beans  
✅ **Pure JDBC** — Full SQL control via fluent-sql-4j DSL; no ORM complexity  
✅ **Spring Transaction Integration** — Automatic binding via `DataSourceUtils`; `@Transactional` works seamlessly  
✅ **Simple Entity Mapping** — Jakarta Persistence annotations (`@Table`, `@Column`, `@Id`); automatic `snake_case` conversion  
✅ **ID Generation Strategies** — Application-provided IDs and database auto-increment (`@GeneratedValue(IDENTITY)`)  
✅ **Type Conversion** — Strings, numbers, booleans, dates (`LocalDate`, `LocalDateTime`), UUIDs  
✅ **Exception Translation** — SQL exceptions translated to Spring's `DataAccessException` hierarchy  
✅ **Dynamic Query Derivation** — Spring Data–style method-name queries (`findByName`, `findByAgeGreaterThan`, …)  
✅ **Custom Query Fragments** — Fluent-sql-4j DSL in Spring Data fragment implementations; multi-datasource safe  
✅ **Multi-DataSource Support** — Bind repository groups to different `DataSource` beans with explicit Spring-style refs  
✅ **Functional Repositories** — `RepositoryResult<T>` return types instead of exceptions; explicit success/failure handling with `fold()`, `map()`, and pattern matching

---

## Compatibility

`fluent-repo-4j` is compatible with **Spring Boot 3.x** and **Spring Boot 4.x**.

### CI-Validated Versions

|  Spring Boot  | Spring Data | Java |     Status     |
|---------------|-------------|------|----------------|
| 3.5.x         | 3.5.x       | 21+  | ✅ CI-validated |
| 4.x (≥ 4.0.5) | 4.x         | 21+  | ✅ CI-validated |

Other patch and minor releases within the same major lines are expected to be compatible.

### How It Works

`fluent-repo-4j` declares Spring dependencies as **`provided`** — they compile against a baseline version
but are **not bundled** in the published JAR. Your application's Spring Boot BOM determines the actual
runtime versions.

---

## Quick Start

Add these dependencies to your application:

```xml
<dependencies>
    <dependency>
        <groupId>io.github.auspishttps://github.com/auspis/fluent-repo-4j-demo</groupId>
        <artifactId>fluent-repo-4j</artifactId>
        <version>1.2.2</version>
    </dependency>

    <!-- Required at application level because fluent-repo-4j uses provided Spring dependencies -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.data</groupId>
        <artifactId>spring-data-commons</artifactId>
    </dependency>
    <dependency>
        <groupId>jakarta.persistence</groupId>
        <artifactId>jakarta.persistence-api</artifactId>
    </dependency>
</dependencies>
```

If you already use a starter that brings these dependencies transitively, avoid duplicate declarations and add just `fluent-repo-4j`.

### 1. Define Your Entity

```java
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;                          // auto-mapped to column "name"

    @Column(name = "email_address")
    private String email;                         // mapped to column "email_address"

    private int age;                              // auto-mapped to column "age"

    // constructors, getters, setters...
}
```

### 2. Create a Repository Interface

```java
public interface UserRepository extends CrudRepository<User, Long> {
    // Inherited: save(), findById(), findAll(), count(), deleteById(), ...

    List<User> findByEmailIgnoreCase(String email);
    List<User> findByNameContainingIgnoreCase(String name);
    List<User> findByAgeGreaterThan(Integer minAge);
}
```

### 3. Enable Repositories

```java
@Configuration(proxyBeanMethods = false)
@EnableFluentRepositories(basePackageClasses = UserRepository.class)
class RepositoryConfiguration {}
```

### 4. Configure the DataSource

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/myapp
    username: root
    password: secret
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 5. Inject and Use

```java
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(String name, String email, int age) {
        return userRepository.save(new User(name, email, age));
    }

    public Optional<User> findUser(Long id) {
        return userRepository.findById(id);
    }

    public List<User> searchByName(String fragment) {
        return userRepository.findByNameContainingIgnoreCase(fragment);
    }

    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
```

The library auto-detects the database dialect, scans for `CrudRepository` interfaces, creates beans, and binds connections to Spring transactions automatically.

---

## Supported Features

|                           Feature                            |    Status    |                               Notes                                |
|--------------------------------------------------------------|--------------|--------------------------------------------------------------------|
| CRUD (`save`, `findById`, `findAll`, `count`, `deleteById`)  | ✅ Supported  | Core built-in operations                                           |
| `@Transactional` integration                                 | ✅ Supported  | Automatic connection binding via Spring                            |
| `@GeneratedValue(IDENTITY)`                                  | ✅ Supported  | Database auto-increment IDs                                        |
| Application-provided IDs                                     | ✅ Supported  | Set ID before `save()`                                             |
| `FluentPersistable<ID>` for custom `isNew()` logic           | ✅ Supported  | Fine-grained control over insert/update                            |
| Simple entity mapping (Jakarta Persistence annotations)      | ✅ Supported  | `@Table`, `@Column`, `@Id`, `@GeneratedValue`, `@Transient`        |
| Exception translation to `DataAccessException`               | ✅ Supported  | Automatic SQL exception handling                                   |
| Dynamic query derivation (Spring Data PartTree-style)        | ✅ Supported  | `findBy…`, `countBy…`, `existsBy…`, `deleteBy…`, pagination/sort   |
| Custom query fragments via `FluentRepositoryContextAware<T>` | ✅ Supported  | DSL-powered custom queries with type-safe mapping                  |
| Multi-datasource repository groups                           | ✅ Supported  | One `@EnableFluentRepositories` block per group                    |
| Functional repositories (`RepositoryResult<T>`)              | ✅ Supported  | `FunctionalCrudRepository`, `FunctionalPagingAndSortingRepository` |
| Object relationships (one-to-many, many-to-many)             | ⚙️ By Design | Keep it simple: use separate repositories and explicit queries     |
| `@GeneratedValue(SEQUENCE)`                                  | 🔜 Planned   | Planned for a future release                                       |
| Persistence context / first-level cache                      | ⚙️ By Design | Not applicable to JDBC; each query returns fresh objects           |

---

## Further Reading

|                             Document                             |                                             Description                                             |
|------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| [USAGE_EXAMPLES.md](data/wiki/USAGE_EXAMPLES.md)                 | Complete examples: ID strategies, UUID keys, transactions, multi-datasource, custom fragments       |
| [DYNAMIC_METHOD_QUERIES.md](data/wiki/DYNAMIC_METHOD_QUERIES.md) | Full reference for query derivation: operators, pagination, sorting, return types, limitations      |
| [ARCHITECTURE.md](data/wiki/ARCHITECTURE.md)                     | Deep dive into internal components, data flow diagrams, connection management, and extension points |
| [TEST.md](data/wiki/TEST.md)                                     | Building the project, running tests, test pyramid, code coverage, and formatting                    |
| [FUNCTIONAL_REPOSITORY.md](data/wiki/FUNCTIONAL_REPOSITORY.md)   | Functional repositories: `RepositoryResult<T>`, error model, usage examples, comparison             |

---

## License

Apache License 2.0. See the [LICENSE](LICENSE) file for details.
