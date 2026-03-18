# Copilot Repository Instructions

## Overview

This repository is a single-module Java library (`jar`) built with Maven and Java 21.
The goal is to provide Spring Data-style repositories backed by pure JDBC and fluent-sql-4j.

## Scope and Boundaries

- Use Spring Data Commons SPI for repository integration.
- Use Spring JDBC primitives for database access and exception translation.
- Keep mapping scope simple (flat entity mapping only, no ORM object graph behavior).
- PartTree query derivation is not implemented yet; proposals or incremental implementation are allowed when aligned with project roadmap.
- Do not introduce unsupported custom query systems outside Spring Data extension points.

## Build and Tooling

- Build tool: Maven (`./mvnw`)
- Java version: 21
- Main fast verification command: `./mvnw clean test`

### Formatting

- Formatting is managed by Spotless Maven Plugin.
- Apply formatting locally with `./mvnw spotless:apply`.
- Validate formatting locally with `./mvnw spotless:check`.

### Git Hook Installation

- Install the repository pre-commit hook using `./mvnw process-resources`.
- The hook runs `./mvnw spotless:apply` before commit and re-stages modified files.

## Coding Rules

- Do not use `var` for local variable declarations.
- Prefer clear, explicit types.
- Prefer immutable fields and constructor injection where possible.
- Keep classes focused and cohesive.
- Prefer Java `record` for simple immutable data carriers with a small number of fields.

### Reflection Policy

- Avoid reflection by default.
- Reflection is allowed only after explicit alternatives analysis and user alignment.
- Use reflection only when non-reflection alternatives are too complex or not viable for the current scope.
- If reflection is used, keep it localized, documented, and covered by focused tests.

### Helper and Utility Classes

- Helper classes must use the `Helper` suffix and live in a `*.helper` package.
- Helper classes must be instantiated and may hold state when that improves readability and reuse.
- Utility classes must use `Util` suffix and must live in a `*.util` package.
- Utility classes must be `final`, expose only `static` methods, and define a private no-args constructor.

## Persistence and Mapping Conventions

- Entity metadata relies on Jakarta Persistence annotations (`@Table`, `@Column`, `@Id`, `@GeneratedValue`).
- Keep naming conversions and mapping behavior consistent with existing `mapping` package conventions.
- When extending ID-generation behavior, preserve current supported strategies and error semantics.

## Testing Rules

- Test framework: JUnit 5.
- Assertions: AssertJ.
- Use unit tests for isolated mapping/repository decision logic.
- Use Spring Boot + H2 integration tests for repository/data-access behaviors.
- Keep test names compact and behavior-oriented.
- Reuse `fluent-sql-4j` `test-support` custom annotations when applicable:
  - `io.github.auspis.fluentsql4j.test.util.annotation.ComponentTest`
  - `io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest`
  - `io.github.auspis.fluentsql4j.test.util.annotation.E2ETest`
- Prefer `@ComponentTest` for multi-class tests with mocked JDBC, and `@IntegrationTest` for real H2-backed repository behavior.
- Reserve `@E2ETest` for full workflow scenarios only when needed; do not overuse slow tests.
- Use `@E2ETest` when testing with testcontainers or other real databases.
- Use helpers from `test-support` (for example SQL capture/assert helpers) to reduce repetitive mocked JDBC setup.

### Test Fixtures and Test Entities

- Prefer using `io.github.auspis.fluentsql4j.test.util.TestDatabaseUtil` for reusable test database setup/cleanup and baseline fixtures instead of ad-hoc SQL in tests.
- Prefer reusing entities from `io.github.auspis.fluentrepo4j.test.domain` (`User`, `CartItem`, `Product`) rather than introducing duplicated local test entities.
- When the required dataset is not fully covered by `TestDatabaseUtil`, compose tests by combining utility methods with a minimal, scenario-specific fixture extension.

### Test Pyramid

Every test class that is not a plain unit test **must** have the correct annotation. Do not rely only on naming conventions.

|    Level    |     Annotation     |         Isolation         |         Database         | Speed  |
|-------------|--------------------|---------------------------|--------------------------|--------|
| Unit        | *(none)*           | Complete                  | No                       | Fast   |
| Component   | `@ComponentTest`   | Real classes, mocked JDBC | No (mocked)              | Fast   |
| Integration | `@IntegrationTest` | Real classes, embedded DB | H2                       | Medium |
| E2E         | `@E2ETest`         | Full system               | Testcontainers (real DB) | Slow   |

Prefer unit tests (base of the pyramid). Add component tests when verifying multi-class interaction. Use integration tests only when embedded H2 is needed. Reserve E2E for smoke validation on real databases.

### Test Execution Commands

- `./mvnw test` — runs unit + component tests only (fast, no database required).
- `./mvnw verify` — runs unit + component + integration (H2) + E2E (Testcontainers, requires Docker).
- `./mvnw verify -Dgroups=e2e` — runs only E2E tests.
- `./mvnw verify -Dgroups=integration` — runs only integration tests.

Surefire excludes `integration,e2e` tags. Failsafe includes them during the `verify` phase.

## Command Guidance for Copilot Changes

When implementing code changes, prefer this local sequence:

1. `./mvnw spotless:apply`
2. `./mvnw clean test`
3. Optional full validation (includes integration and E2E, requires Docker): `./mvnw clean verify`
4. Optional final formatting check: `./mvnw spotless:check`

If a change touches formatting-sensitive files (`.java`, `pom.xml`, `.md`), always run Spotless before finalizing.

## Documentation Expectations

- Keep public-facing behavior documented in `README.md` and wiki docs where relevant.
- Document important constraints and decisions near the code when behavior is non-obvious.
- Ensure generated guidance and examples are aligned with currently supported repository capabilities.

