---

goal: Separare operazioni di lettura e scrittura nei repository functional con risultati espliciti found/not-found/failure
version: 1.1
date_created: 2026-05-12
last_updated: 2026-05-12
owner: fluent-repo-4j
status: Completed
tags: [feature, architecture, functional-repository, api, migration]
--------------------------------------------------------------------

# Introduction

![Status: Completed](https://img.shields.io/badge/status-Completed-brightgreen)

Questo piano definisce l implementazione della separazione tra repository functional di lettura e repository functional di scrittura, eliminando l uso di Optional nel caso found/not found lato lettura e introducendo un modello esplicito a tre esiti: found, not-found, failure.

Il cambiamento e intenzionalmente BREAKING (nessuna backward compatibility). La rottura API deve essere documentata in modo esplicito in `data/release-notes/RELEASE_NOTES_v1.4.0.md`.

Il piano mantiene i vincoli architetturali del progetto (Spring Data SPI + JDBC + fluent-sql-4j).

## 1. Requirements & Constraints

- REQ-001: Introdurre un contratto funzionale di lettura con tre scenari espliciti: found, not-found, failure.
- REQ-002: Introdurre un contratto funzionale di scrittura con due scenari: success, failure.
- REQ-003: Ridurre la complessita delle firme eliminando pattern come RepositoryResult<Optional<T>> nelle letture.
- REQ-004: Mantenere il supporto alle query derivate Spring Data per repository functional.
- REQ-005: Per la lettura, mappare gli errori infrastrutturali nel risultato `Failure` (no eccezione propagata verso il chiamante della functional read API).
- REQ-006: Accettare breaking change immediato sulle API functional esistenti.
- REQ-007: Documentare la breaking change in `data/release-notes/RELEASE_NOTES_v1.4.0.md` con sezione migrazione before/after.
- REQ-008: Aggiornare documentazione utente e tecnica con esempi before/after.
- ARC-001: Rimanere dentro Spring Data Commons SPI, Spring JDBC e fluent-sql-4j.
- ARC-002: Non introdurre framework query custom fuori dagli extension point Spring Data gia in uso.
- CON-001: Java 21, no local var, tipi espliciti.
- CON-002: Mantenere comportamento flat mapping (nessun comportamento ORM graph).
- TST-001: Copertura minima 85% per classi nuove o modificate.
- TST-002: Applicare test pyramid: unit -> component -> integration (H2) -> e2e solo se necessario.
- GUD-001: Mantenere naming coerente tra read/write (`Failure` in entrambe le API).

## 2. Implementation Steps

### Implementation Phase 1

- GOAL-001: Definire il nuovo modello di risultato e i nuovi contratti functional separati.

|   Task   |                                                                                  Description                                                                                  | Completed |    Date    |
|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|------------|
| TASK-001 | Definire modello ReadResult<T> sealed con varianti Found<T>, NotFound<T>, Failure<T> in nuovo package functional.read (scelta preferita) o functional.result.read.            | ✅         | 2026-05-12 |
| TASK-002 | Definire modello WriteResult<T> sealed con varianti Success<T>, Failure<T> in nuovo package functional.write o functional.result.write.                                       | ✅         | 2026-05-12 |
| TASK-003 | Introdurre nuovo tipo dedicato WriteResult senza riuso/alias di RepositoryResult (breaking change esplicito).                                                                 | ✅         | 2026-05-12 |
| TASK-004 | Definire nuove interfacce FunctionalReadRepository<T, ID> e FunctionalWriteRepository<T, ID> con firme esplicite senza Optional nelle letture.                                | ✅         | 2026-05-12 |
| TASK-005 | Definire interfacce di paging/sorting lettura dedicate (es. FunctionalReadPagingAndSortingRepository) con ritorni ReadResult<List<T>> e ReadResult<Page<T>> dove applicabile. | ✅         | 2026-05-12 |
| TASK-006 | Confermare che la scrittura non prevede API di paging/sorting (scope limitato a save/delete/update).                                                                          | ✅         | 2026-05-12 |
| TASK-007 | Redigere sezione decisionale in plan con rationale trade-off (verbosity API, chiarezza semantica, impatto breaking).                                                          | ✅         | 2026-05-12 |

Completion criteria fase 1:
- Contratti API definiti in codice compilabile.
- Nomenclatura e package definitivi stabiliti.
- Decisione breaking approvata e scope write/read formalizzato.

### Implementation Phase 2

- GOAL-002: Implementare le nuove classi repository e adapter interni sul core condiviso.

|   Task   |                                                          Description                                                           | Completed |    Date    |
|----------|--------------------------------------------------------------------------------------------------------------------------------|-----------|------------|
| TASK-008 | Implementare FunctionalReadFluentRepository<T, ID> che delega a CoreRepositoryOperations e mappa i risultati su ReadResult.    | ✅         | 2026-05-12 |
| TASK-009 | Implementare FunctionalWriteFluentRepository<T, ID> che delega a CoreRepositoryOperations e mappa i risultati su WriteResult.  | ✅         | 2026-05-12 |
| TASK-010 | Estrarre helper di mapping comuni per evitare duplicazione tra repository functional standard e nuovi split repository.        | ✅         | 2026-05-12 |
| TASK-011 | Gestire semantica not-found per findById e query singole in modo esplicito senza Optional.                                     | ✅         | 2026-05-12 |
| TASK-012 | Mantenere semantica count/exists/findAll in lettura con risultato Found anche se lista vuota, salvo policy diversa concordata. | ✅         | 2026-05-12 |
| TASK-013 | Preservare cattura dei fallimenti di dominio lato scrittura (es. optimistic locking) come Failure dedicata.                    | ✅         | 2026-05-12 |
| TASK-014 | Mappare DataAccessException in ReadResult.Failure (con cause preservata) per tutte le operazioni read.                         | ✅         | 2026-05-12 |

Completion criteria fase 2:
- Nuove implementazioni funzionanti su CRUD read/write.
- Nessuna regressione del core JDBC condiviso.
- Branch principali coperti da test unitari.

### Implementation Phase 3

- GOAL-003: Integrare factory Spring Data e query derivation con i nuovi contratti.

|   Task   |                                                                             Description                                                                             | Completed |    Date    |
|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|------------|
| TASK-015 | Aggiornare FluentRepositoryFactory.isFunctionalRepository per riconoscere le nuove interfacce read/write.                                                           | ✅         | 2026-05-12 |
| TASK-016 | Aggiornare getTargetRepository e getRepositoryBaseClass per costruire l implementazione corretta in base all interfaccia estesa.                                    | ✅         | 2026-05-12 |
| TASK-017 | Adeguare la pipeline query runtime (FluentRepositoryQuery/lookup strategy) per unwrapping di ReadResult e WriteResult nei metodi derivati.                          | ✅         | 2026-05-12 |
| TASK-018 | Definire regole univoche di mapping per metodi derivati: findBy -> Found/NotFound, existsBy -> Found<boolean>, countBy -> Found<long>, deleteBy -> Success/Failure. | ✅         | 2026-05-12 |
| TASK-019 | Validare compatibilita con custom fragments e FluentRepositoryContextAware in configurazioni multi datasource.                                                      | ✅         | 2026-05-12 |

Completion criteria fase 3:
- Repository proxy creati correttamente da Spring.
- Query derivate operative con nuovi result type.
- Multi datasource invariato.

### Implementation Phase 4

- GOAL-004: Applicare breaking change e aggiornare documentazione/release notes.

|   Task   |                                                                             Description                                                                             | Completed |    Date    |
|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|------------|
| TASK-020 | Rimuovere/sostituire le API functional legacy (FunctionalCrudRepository, FunctionalPagingAndSortingRepository, RepositoryResult) dai percorsi pubblici interessati. | ✅         | 2026-05-12 |
| TASK-021 | Aggiornare messaggi di errore e hint configurazione per menzionare nuove interfacce.                                                                                | ✅         | 2026-05-12 |
| TASK-022 | Creare `data/release-notes/RELEASE_NOTES_v1.4.0.md` con sezione BREAKING, migration guide e before/after examples.                                                  | ✅         | 2026-05-12 |

Completion criteria fase 4:
- Build passa con nuove API split read/write.
- Breaking change documentata in release notes v1.4.0.
- Messaggi e guide migrazione coerenti tra README/wiki/release note.

### Implementation Phase 5

- GOAL-005: Test completi, documentazione e quality gates.

|   Task   |                                                      Description                                                      | Completed |    Date    |
|----------|-----------------------------------------------------------------------------------------------------------------------|-----------|------------|
| TASK-023 | Aggiungere unit test per ReadResult e WriteResult (costruttori, invarianti, combinatori).                             | ✅         | 2026-05-12 |
| TASK-024 | Aggiornare/aggiungere test di FunctionalReadFluentRepository e FunctionalWriteFluentRepository per tutti i branch.    | ✅         | 2026-05-12 |
| TASK-025 | Aggiornare integration test esempio con nuovo repository split (lettura/scrittura) e query derivate.                  | ✅         | 2026-05-12 |
| TASK-026 | Aggiornare README.md e wiki: FUNCTIONAL_REPOSITORY.md, USAGE_EXAMPLES.md, ARCHITECTURE.md, DYNAMIC_METHOD_QUERIES.md. | ✅         | 2026-05-12 |
| TASK-027 | Eseguire quality gates: ./mvnw spotless:apply, ./mvnw clean test, opzionale ./mvnw clean verify.                      | ✅         | 2026-05-12 |
| TASK-028 | Verificare soglia copertura 85% su classi nuove/modificate.                                                           | ✅         | 2026-05-12 |

Completion criteria fase 5:
- Tutti i test verdi.
- Documentazione sincronizzata.
- Copertura conforme.

## 3. Alternatives

- ALT-001: Mantenere unico RepositoryResult con terza variante NotFound globale. Scartato: non adatto alla scrittura, aumenta ambiguita.
- ALT-002: Usare solo eccezioni checked per not-found. Scartato: peggiora ergonomia e rompe stile functional.
- ALT-003: Lasciare Optional nelle letture e aggiungere utility helper. Scartato: non risolve chiarezza firme.
- ALT-004: Introdurre un unico Result<T, E> generico con taxonomy complessa. Scartato: eccessiva complessita per il dominio corrente.
- ALT-005: Mantenere API legacy con deprecazione a 1 release. Scartato: decisione esplicita di accettare breaking change immediato.

## 4. Dependencies

- DEP-001: Spring Data Commons SPI esistente (RepositoryFactorySupport, QueryLookupStrategy).
- DEP-002: CoreRepositoryOperations e SaveDecisionResolver.
- DEP-003: fluent-sql-4j DSL e mappatori query runtime.
- DEP-004: Suite test JUnit5 + AssertJ + fixture TestDatabaseUtil.

## 5. Files

- FILE-001: src/main/java/io/github/auspis/fluentrepo4j/functional/read/ReadResult.java (nuovo)
- FILE-002: src/main/java/io/github/auspis/fluentrepo4j/functional/write/WriteResult.java (nuovo)
- FILE-003: src/main/java/io/github/auspis/fluentrepo4j/functional/read/FunctionalReadRepository.java (nuovo)
- FILE-004: src/main/java/io/github/auspis/fluentrepo4j/functional/write/FunctionalWriteRepository.java (nuovo)
- FILE-005: src/main/java/io/github/auspis/fluentrepo4j/repository/FunctionalReadFluentRepository.java (nuovo)
- FILE-006: src/main/java/io/github/auspis/fluentrepo4j/repository/FunctionalWriteFluentRepository.java (nuovo)
- FILE-007: src/main/java/io/github/auspis/fluentrepo4j/repository/FluentRepositoryFactory.java
- FILE-008: src/main/java/io/github/auspis/fluentrepo4j/query/runtime/FluentRepositoryQuery.java
- FILE-009: src/main/java/io/github/auspis/fluentrepo4j/query/runtime/FluentQueryLookupStrategy.java
- FILE-010: src/main/java/io/github/auspis/fluentrepo4j/functional/RepositoryResult.java (rimozione o riduzione uso pubblico)
- FILE-011: src/main/java/io/github/auspis/fluentrepo4j/functional/FunctionalCrudRepository.java (rimozione o sostituzione)
- FILE-012: src/main/java/io/github/auspis/fluentrepo4j/functional/FunctionalPagingAndSortingRepository.java (rimozione o sostituzione)
- FILE-013: src/test/java/io/github/auspis/fluentrepo4j/functional/*
- FILE-014: src/test/java/io/github/auspis/fluentrepo4j/repository/*Functional*Test.java
- FILE-015: src/test/java/io/github/auspis/fluentrepo4j/example/FunctionalUserRepository.java (migrazione esempio)
- FILE-016: src/test/java/io/github/auspis/fluentrepo4j/example/FunctionalUserRepositoryIntegrationTest.java
- FILE-017: README.md
- FILE-018: data/wiki/FUNCTIONAL_REPOSITORY.md
- FILE-019: data/wiki/USAGE_EXAMPLES.md
- FILE-020: data/wiki/ARCHITECTURE.md
- FILE-021: data/wiki/DYNAMIC_METHOD_QUERIES.md
- FILE-022: data/release-notes/RELEASE_NOTES_v1.4.0.md

## 6. Testing

- TEST-001: ReadResult unit test: Found, NotFound, Failure, map/fold/peek/orElse semantics.
- TEST-002: WriteResult unit test: Success, Failure, map/fold/peek/orElse semantics.
- TEST-003: FunctionalReadFluentRepository unit test: findById found/not-found, findAll, exists, count, paging offset edge.
- TEST-004: FunctionalWriteFluentRepository unit test: save branches (insert/update/error), optimistic locking -> failure, delete semantics.
- TEST-005: Factory unit test: riconoscimento nuove interfacce e base class corretta.
- TEST-006: Query runtime unit test: unwrapping nuovi result type su metodi derivati.
- TEST-007: Integration test H2: repository split read/write con derived queries.
- TEST-008: Test specifico mapping DataAccessException -> ReadResult.Failure.
- TEST-009: Validazione contenuti release note v1.4.0 (breaking + migration examples coerenti con API finale).

## 7. Risks & Assumptions

- RISK-001: Rottura API per utenti che usano firme RepositoryResult<Optional<T>> nei repository custom.
- RISK-002: Aumento complessita nella pipeline query derivation per mapping di nuovi wrapper.
- RISK-003: Ambiguita semantica su collezioni vuote (Found empty vs NotFound) se non definita chiaramente.
- RISK-004: Gestione incoerente dei side effect se il mapping a Failure viene applicato solo a sottoinsiemi di metodi read.
- ASSUMPTION-001: NotFound si applica alle query singole (findOne/findById) e non alle collezioni salvo decisione diversa.
- ASSUMPTION-002: La write API non include paging/sorting.

## 8. Related Specifications / Further Reading

- README.md
- data/wiki/FUNCTIONAL_REPOSITORY.md
- data/wiki/ARCHITECTURE.md
- data/wiki/DYNAMIC_METHOD_QUERIES.md
- data/wiki/USAGE_EXAMPLES.md
- src/main/java/io/github/auspis/fluentrepo4j/repository/FluentRepositoryFactory.java
- src/main/java/io/github/auspis/fluentrepo4j/repository/FunctionalReadFluentRepository.java
- src/main/java/io/github/auspis/fluentrepo4j/repository/FunctionalWriteFluentRepository.java
- src/main/java/io/github/auspis/fluentrepo4j/functional/read/ReadResult.java
- src/main/java/io/github/auspis/fluentrepo4j/functional/write/WriteResult.java

## 9. Open Questions Resolution

- QST-001 (Resolved): Collection read queries return `Found` with empty list (never `NotFound`).
- QST-002 (Resolved): `existsBy` and `countBy` always return `Found<Boolean|Long>` (including `false` and `0`).
- QST-003 (Resolved): `deleteById` in write APIs returns `Success(true|false)`.
- QST-004 (Resolved): Final interface names are `FunctionalReadRepository` and `FunctionalWriteRepository`.

## 10. Detailed TODO Checklist (Execution-Ready)

Execution checklist used to track implementation progress phase-by-phase.

### Phase 0 - Decision Lock (pre-implementation)

- [x] TODO-0001: Resolve QST-001 and document the final rule for collection-returning read methods.
- [x] TODO-0002: Resolve QST-002 and document the final rule for `existsBy`/`countBy` read methods.
- [x] TODO-0003: Resolve QST-003 and document final write deletion semantics.
- [x] TODO-0004: Resolve QST-004 and lock final interface names.
- [x] TODO-0005: Update section 7 assumptions to reflect final decisions.
- [x] TODO-0006: Mark all resolved questions in section 9 as closed (or remove section 9 once all are decided).

### Phase 1 - New Functional Contracts and Result Types

- [x] TODO-0101: Create `ReadResult` sealed model with `Found`, `NotFound`, `Failure`.
- [x] TODO-0102: Add invariant validation for `ReadResult` variants (non-null payloads/messages).
- [x] TODO-0103: Create `WriteResult` sealed model with `Success`, `Failure`.
- [x] TODO-0104: Add invariant validation for `WriteResult` variants.
- [x] TODO-0105: Add shared combinators for `ReadResult` (`map`, `fold`, `peek`, `orElse` variants).
- [x] TODO-0106: Add shared combinators for `WriteResult` (`map`, `fold`, `peek`, `orElse` variants).
- [x] TODO-0107: Define `FunctionalReadRepository<T, ID>` without `Optional` for single-entity reads.
- [x] TODO-0108: Define `FunctionalWriteRepository<T, ID>` for save/update/delete operations.
- [x] TODO-0109: Define read paging/sorting interface (for `Sort` and `Pageable` reads only).
- [x] TODO-0110: Confirm and document that write APIs do not include paging/sorting.

### Phase 2 - Repository Implementations on Shared Core

- [x] TODO-0201: Add `FunctionalReadFluentRepository<T, ID>` implementation.
- [x] TODO-0202: Add `FunctionalWriteFluentRepository<T, ID>` implementation.
- [x] TODO-0203: Wire read repository methods to `CoreRepositoryOperations` read primitives.
- [x] TODO-0204: Wire write repository methods to `CoreRepositoryOperations` save/delete primitives.
- [x] TODO-0205: Implement explicit not-found mapping for single-entity reads.
- [x] TODO-0206: Implement explicit found mapping for list/page reads based on final policy.
- [x] TODO-0207: Implement domain-failure mapping for write-side failures.
- [x] TODO-0208: Implement infra-error mapping (`DataAccessException`) to read-side `Failure`.
- [x] TODO-0209: Add internal mapper/helper utilities to avoid duplicated conversion logic.
- [x] TODO-0210: Verify no behavior change in shared JDBC core (`CoreRepositoryOperations`).

### Phase 3 - Spring Data Factory and Query Runtime Integration

- [x] TODO-0301: Update functional interface detection in `FluentRepositoryFactory`.
- [x] TODO-0302: Update target repository creation routing for read vs write repositories.
- [x] TODO-0303: Update repository base class routing for read vs write interfaces.
- [x] TODO-0304: Extend query return-type unwrapping for `ReadResult`.
- [x] TODO-0305: Extend query return-type unwrapping for `WriteResult`.
- [x] TODO-0306: Apply final mapping rules for derived methods (`findBy`, `existsBy`, `countBy`, `deleteBy`).
- [x] TODO-0307: Validate method invocation pipeline with cache (`FluentQueryLookupStrategy`) after wrapper changes.
- [x] TODO-0308: Validate custom fragment behavior (`FluentRepositoryContextAware`) is unaffected.
- [x] TODO-0309: Validate multi-datasource behavior with read/write split repositories.

### Phase 4 - Breaking Change Application and Public API Cleanup

- [x] TODO-0401: Remove or replace legacy functional interfaces from public API surface.
- [x] TODO-0402: Remove or demote old `RepositoryResult` usage from public functional API paths.
- [x] TODO-0403: Update error and configuration hint messages for the new interface names.
- [x] TODO-0404: Create `data/release-notes/RELEASE_NOTES_v1.4.0.md`.
- [x] TODO-0405: Add explicit **BREAKING** section in release notes.
- [x] TODO-0406: Add migration guidance with before/after API signatures.
- [x] TODO-0407: Add migration examples for read single result, read list/page, and write delete semantics.
- [x] TODO-0408: Add explicit note about infrastructure error behavior in read APIs.

### Phase 5 - Testing and Documentation Completion

- [x] TODO-0501: Add unit tests for `ReadResult` constructors/invariants.
- [x] TODO-0502: Add unit tests for `ReadResult` combinators.
- [x] TODO-0503: Add unit tests for `WriteResult` constructors/invariants.
- [x] TODO-0504: Add unit tests for `WriteResult` combinators.
- [x] TODO-0505: Add unit tests for `FunctionalReadFluentRepository` found/not-found/failure branches.
- [x] TODO-0506: Add unit tests for `FunctionalWriteFluentRepository` success/failure branches.
- [x] TODO-0507: Add unit tests for read infra error mapping to `Failure`.
- [x] TODO-0508: Update factory tests for new read/write interface routing.
- [x] TODO-0509: Update query runtime tests for new wrapper unwrapping and derived query mappings.
- [x] TODO-0510: Update integration tests for read/write split repositories in H2.
- [x] TODO-0511: Update `README.md` with new API and migration summary.
- [x] TODO-0512: Update `data/wiki/FUNCTIONAL_REPOSITORY.md` with final semantics and examples.
- [x] TODO-0513: Update `data/wiki/USAGE_EXAMPLES.md` with read/write split examples.
- [x] TODO-0514: Update `data/wiki/ARCHITECTURE.md` with new factory/runtime flow.
- [x] TODO-0515: Update `data/wiki/DYNAMIC_METHOD_QUERIES.md` with final return type mapping rules.

### Phase 6 - Verification Gates and Exit Criteria

- [x] TODO-0601: Run `./mvnw spotless:apply`.
- [x] TODO-0602: Run `./mvnw clean test`.
- [x] TODO-0603: Run optional `./mvnw clean verify` (if environment supports full suite).
- [x] TODO-0604: Validate minimum 85% coverage for all new/changed production classes.
- [x] TODO-0605: Verify release notes and documentation are aligned with implemented behavior.
- [x] TODO-0606: Verify no legacy functional API references remain in public docs.
- [x] TODO-0607: Final pass: confirm section 2 tasks and section 10 TODOs are all marked completed.

### Definition of Done (cross-phase)

- [x] TODO-DONE-01: All phase checklists (TODO-0001 through TODO-0607) are completed.
- [x] TODO-DONE-02: Build/tests/formatting gates are green.
- [x] TODO-DONE-03: Breaking change is fully documented in `data/release-notes/RELEASE_NOTES_v1.4.0.md`.
- [x] TODO-DONE-04: Public docs show only the new read/write functional API model.

