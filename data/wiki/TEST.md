# Tests

This document is the canonical reference for building the project, running tests, and generating coverage reports.

## Table of Contents

1. [Building the Project](#building-the-project)
2. [Test Pyramid](#test-pyramid)
3. [Running Tests](#running-tests)
4. [Test Annotations and Isolation](#test-annotations-and-isolation)
5. [Writing Tests](#writing-tests)
6. [Test Coverage (JaCoCo)](#test-coverage-jacoco)
7. [Code Formatting](#code-formatting)
8. [Debugging and SQL Logging](#debugging-and-sql-logging)

---

## Building the Project

```bash
./mvnw clean install              # full build with all tests
./mvnw clean install -DskipTests  # build without running tests
./mvnw clean test                 # compile and run unit + component tests (fast)
./mvnw clean verify               # compile and run all tests (requires Docker for E2E)
```

---

## Test Pyramid

Every test class that is not a plain unit test **must** carry the correct annotation. Do not rely solely on naming conventions.

|    Level    |     Annotation     |         Isolation         |         Database         | Speed  |
|-------------|--------------------|---------------------------|--------------------------|--------|
| Unit        | *(none)*           | Complete                  | No                       | Fast   |
| Component   | `@ComponentTest`   | Real classes, mocked JDBC | No (mocked)              | Fast   |
| Integration | `@IntegrationTest` | Real classes, embedded DB | H2                       | Medium |
| E2E         | `@E2ETest`         | Full system               | Testcontainers (real DB) | Slow   |

**Prefer unit tests** (base of the pyramid). Add component tests when verifying multi-class interaction. Use integration tests only when embedded H2 is needed. Reserve E2E tests for smoke validation on real databases.

---

## Running Tests

|               Command                |                    What runs                     | Requires Docker |
|--------------------------------------|--------------------------------------------------|-----------------|
| `./mvnw test`                        | Unit + Component (fast, no database)             | No              |
| `./mvnw verify`                      | All tests (Unit + Component + Integration + E2E) | Yes (for E2E)   |
| `./mvnw test -Dgroups=component`     | Component tests only                             | No              |
| `./mvnw verify -Dgroups=integration` | Integration tests only (H2)                      | No              |
| `./mvnw verify -Dgroups=e2e`         | E2E tests only (Testcontainers)                  | Yes             |

**How it works:** Surefire (invoked by `./mvnw test`) excludes the `integration` and `e2e` tags, so only unit and component tests run in the fast path. Failsafe (invoked during `./mvnw verify`) picks up those same tags and runs integration and E2E tests against real or embedded databases.

---

## Test Annotations and Isolation

### `@ComponentTest`

Use for multi-class tests where real production classes collaborate but JDBC is mocked.

- No real database required
- Fast execution
- Import: `io.github.auspis.fluentsql4j.test.util.annotation.ComponentTest`

```java
@ComponentTest
class SaveDecisionResolverTest {
    // Real SaveDecisionResolver + FluentEntityInformation, mocked existsById predicate
}
```

### `@IntegrationTest`

Use for tests that exercise real repository behavior against an embedded H2 database.

- Requires Spring context and H2
- Medium speed
- Import: `io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest`

```java
@IntegrationTest
class UserRepositoryIT {
    @Autowired
    private UserRepository userRepository;
    // ...
}
```

### `@E2ETest`

Use for full end-to-end scenarios against a real database using Testcontainers. Reserve for smoke validation only.

- Requires Docker
- Slow
- Import: `io.github.auspis.fluentsql4j.test.util.annotation.E2ETest`

```java
@E2ETest
class UserRepositoryE2ETest {
    // PostgreSQL or MySQL via Testcontainers
}
```

---

## Writing Tests

### Assertions

Use **AssertJ** for all assertions. Do not use JUnit `assertEquals` directly.

```java
assertThat(repository.findById(1L)).isPresent();
assertThat(result.getName()).isEqualTo("Alice");
assertThat(repository.count()).isEqualTo(3L);
```

### Test Fixtures and Entities

- Prefer reusing entities from `io.github.auspis.fluentrepo4j.test.domain` (`User`, `CartItem`, `Product`) rather than introducing duplicated local test entities.
- Prefer `io.github.auspis.fluentsql4j.test.util.TestDatabaseUtil` for reusable test database setup, cleanup, and baseline fixtures instead of ad-hoc SQL.
- When the required dataset is not fully covered by `TestDatabaseUtil`, extend it minimally with scenario-specific fixture SQL.

### Integration Test Setup (H2 Example)

```java
@IntegrationTest
class UserRepositoryIT {

    private Connection connection;
    private SimpleFluentRepository<User, Long> repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = TestDatabaseUtil.createH2Connection();
        TestDatabaseUtil.createUsersTable(connection);

        DataSource dataSource = new SingleConnectionDataSource(connection, true);
        DSL dsl = DialectDetector.detect(dataSource, DSLRegistry.createWithServiceLoader());
        FluentConnectionProvider connectionProvider = new FluentConnectionProvider(dataSource);
        FluentEntityInformation<User, Long> entityInfo = new FluentEntityInformation<>(User.class);

        repository = new SimpleFluentRepository<>(entityInfo, connectionProvider, dsl);
    }

    @AfterEach
    void tearDown() throws SQLException {
        TestDatabaseUtil.closeConnection(connection);
    }

    @Test
    void save_insertsNewEntity() {
        User user = new User("Alice", "alice@example.com").withId(1L);
        repository.save(user);
        assertThat(repository.findById(1L)).isPresent();
    }
}
```

### Test Naming

Keep test names compact and behaviour-oriented. Prefer the pattern `methodName_scenario` or `methodName_expectedBehavior`.

```java
@Test void save_insertsNewEntity() { ... }
@Test void save_updatesExistingEntity() { ... }
@Test void findById_returnsEmptyWhenNotFound() { ... }
```

### SQL Capture Helpers

Use helpers from `test-support` (SQL capture/assert helpers) to reduce repetitive mocked JDBC setup in component tests.

---

## Test Coverage (JaCoCo)

### Generating a Coverage Report

Generate an HTML report after running tests:

```bash
./mvnw clean test jacoco:report
```

The report is generated at `target/site/jacoco/index.html`.

If the JaCoCo `report` goal is bound to the `verify` phase in the POM:

```bash
./mvnw clean verify
```

For CI / non-interactive builds:

```bash
./mvnw -B clean verify jacoco:report
```

### How JaCoCo Is Wired

- The `prepare-agent` execution starts the JaCoCo agent before tests.
- `prepare-agent` exposes the agent JVM options in the `jacocoArgLine` property.
- Both `maven-surefire-plugin` (unit/component) and `maven-failsafe-plugin` (integration/E2E) include `${jacocoArgLine}` in their `argLine` so all test levels are instrumented.

### Coverage Enforcement

The JaCoCo `check` goal can enforce coverage thresholds and fail the build when thresholds are not met. This repository does not enable `check` by default. To enable it, add a `check` execution bound to `verify` with the desired limits (for example, `line >= 0.75`, `branch >= 0.70`) and run `mvn verify` in CI.

### Troubleshooting

|              Problem               |                                                 Solution                                                  |
|------------------------------------|-----------------------------------------------------------------------------------------------------------|
| No report generated                | Run `./mvnw jacoco:report` explicitly, or verify that `report` is bound to an appropriate lifecycle phase |
| Integration tests not instrumented | Ensure `${jacocoArgLine}` is added to the Failsafe `argLine` configuration                                |
| Multi-module aggregation           | Collect per-module exec files and use `report-aggregate` or `merge` + `report` in an aggregator POM       |

---

## Code Formatting

Formatting is managed by the **Spotless** Maven plugin using the Google Java Format style.

```bash
./mvnw spotless:apply   # apply formatting
./mvnw spotless:check   # verify formatting without modifying files
```

### Pre-Commit Hook

A pre-commit Git hook that runs `spotless:apply` automatically and re-stages modified files can be installed with:

```bash
./mvnw process-resources
```

Once installed, every `git commit` will format Java source files before they are committed.

---

## Debugging and SQL Logging

Enable Spring JDBC debug logging to see every SQL statement executed:

```yaml
logging:
  level:
    org.springframework.jdbc: DEBUG
```

This prints the SQL and bind parameters for every `PreparedStatement` executed through the repository layer.
