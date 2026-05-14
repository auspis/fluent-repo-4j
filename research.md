# Research: Functional Repositories e RepositoryResult in fluent-repo-4j

## Obiettivo della ricerca

Questo documento spiega in modo approfondito come funziona la parte functional dei repository in fluent-repo-4j, con focus su `RepositoryResult`, wiring Spring Data, semantica operativa (save/find/delete/paging/query derivate), modello errori e vincoli.

La ricerca e basata su:
- codice di produzione in `src/main/java`
- test unitari e di integrazione in `src/test/java`
- documentazione ufficiale (`README.md` e wiki `data/wiki/*.md`)

## Executive summary

La modalita functional introduce due interfacce repository (`FunctionalCrudRepository`, `FunctionalPagingAndSortingRepository`) che espongono quasi le stesse capacita CRUD/paging di Spring Data, ma invece di usare return type void/eccezioni unchecked per i casi di dominio, ritornano sempre `RepositoryResult<T>`.

In pratica:
- successi ed esiti attesi sono valori espliciti (`Success<T>`)
- failure di dominio sono valori espliciti (`Failure<T>`)
- errori infrastrutturali (JDBC/SQL/connessione) continuano a propagare come `DataAccessException`

L implementazione concreta e `FunctionalFluentRepository`, costruita dalla stessa factory Spring (`FluentRepositoryFactory`) che gestisce anche il repository standard (`FluentRepository`). Entrambe le varianti delegano le operazioni SQL a un nucleo condiviso: `CoreRepositoryOperations`.

## Architettura end-to-end (bootstrap -> runtime)

### 1) Abilitazione e scansione repository

L entrypoint e `@EnableFluentRepositories`:
- importa `FluentRepositoriesRegistrar`
- espone parametri di configurazione per gruppi repository (basePackages, refs bean specifici, transaction manager)

`FluentRepositoriesRegistrar` estende `RepositoryBeanDefinitionRegistrarSupport` e collega l estensione `FluentRepositoryConfigExtension`.

### 2) Binding degli attributi annotation alla factory bean

`FluentRepositoryConfigExtension.postProcess(...)` trasferisce in modo opzionale i riferimenti definiti su `@EnableFluentRepositories` nelle property della factory bean:
- `dataSourceRef` -> `dataSource`
- `dslRegistryRef` -> `dslRegistry`
- `connectionProviderRef` -> `connectionProvider`
- `dslRef` -> `dsl`

Questa fase rende possibile configurare gruppi repository su datasource differenti, oppure passare infrastruttura completamente custom.

### 3) Creazione factory repository

`FluentRepositoryFactoryBean`:
- risolve `FluentConnectionProvider` e `DSL`
- costruisce `FluentRepositoryFactory`

Risoluzione e precedenze (importante):
- se `connectionProvider` e gia impostato, viene usato direttamente
- altrimenti, viene risolto da `dataSource` (esplicito o bean unico/primary)
- per `dsl`: se `dsl` e gia impostato, viene usato direttamente
- altrimenti viene derivato con `DialectDetector.detect(resolveDataSource(), resolveDslRegistry())`

Messaggi di errore sono espliciti in caso di bean mancanti/ambigui (testati in `FluentRepositoryFactoryBeanTest`).

### 4) Scelta repository standard vs functional

`FluentRepositoryFactory.getTargetRepository(...)` usa `isFunctionalRepository(...)`:
- se interfaccia estende `FunctionalCrudRepository` o `FunctionalPagingAndSortingRepository` -> istanzia `FunctionalFluentRepository`
- altrimenti -> `FluentRepository`

Entrambi ricevono `CoreRepositoryOperations` condiviso.

### 5) Query derivate e proxy Spring Data

`FluentRepositoryFactory.getQueryLookupStrategy(...)` registra `FluentQueryLookupStrategy`, che risolve query derivate stile PartTree (`findBy...`, `countBy...`, `existsBy...`, `deleteBy...`) e cache per method.

Di conseguenza, anche i repository functional beneficiano della stessa pipeline di query derivata.

## Il cuore functional: RepositoryResult

`RepositoryResult<T>` e una sealed interface con due varianti:
- `Success<T>(T value)`
- `Failure<T>(String message, Throwable cause)`

Garanzie importanti:
- `Success.value` non puo essere null
- `Failure.message` non puo essere null o blank
- `Failure.cause` puo essere null

Combinatori disponibili:
- `map(Function<T,U>)`
- `fold(onSuccess, onFailure)`
- `peek(Consumer<T>)`
- `orElseThrow()`
- `orElse(defaultValue)`
- `isSuccess()` / `isFailure()`

Test dedicati (`RepositoryResultTest`) coprono validazioni costruttore e combinatori.

## Semantica operativa della variante functional

L implementazione e in `FunctionalFluentRepository<T,ID>`.

### Save

`save(entity)`:
- usa `SaveDecisionResolver` per decidere `SaveAction`
- dispatch su:
- `INSERT_PROVIDED_ID` -> `core.insertWithProvidedId(entity)`
- `INSERT_AUTO_ID` -> `core.insertWithIdentity(entity)`
- `UPDATE` -> `core.update(entity)`
- `ERROR` -> ritorna `Failure` con messaggio descrittivo

Inoltre:
- se entity implementa `FluentPersistable`, dopo successo chiama `markPersisted()`
- se `core.update` lancia `OptimisticLockingFailureException`, la variante functional la converte in `Failure` (non rilancia)

`saveAll(entities)`:
- salva uno ad uno
- si ferma alla prima `Failure`
- ritorna `Success<List<S>>` solo se tutte le save vanno bene

### Find

- `findById` -> `Success(Optional<T>)`
- `existsById` -> `Success<Boolean>`
- `findAll` -> `Success<List<T>>`
- `findAllById` -> `Success<List<T>>` (ID mancanti vengono ignorati, come comportamento Spring Data classico)
- `count` -> `Success<Long>`

Assenza dato non e failure:
- `findById` su ID inesistente produce `Success(Optional.empty())`

### Paging e sorting

- `findAll(Sort)` -> `Success<List<T>>`
- `findAll(Pageable)` -> `Success<Page<T>>`

Gestione robusta edge case:
- se totalElements = 0, ritorna pagina vuota con metadata coerenti
- se offset oltre il totale, ritorna pagina vuota senza errore

### Delete

- `deleteById` -> `Success(true/false)` in base alle righe affette
- `delete(entity)`:
  - se ID null -> `Failure("Cannot delete entity with null ID")`
  - altrimenti delega a `deleteById`
- `deleteAllById` -> `Success<Long>` con count effettivo
- `deleteAll(entities)` -> `Success<Long>` con count effettivo
- `deleteAll()` -> `Success<Long>` con count totale cancellato

## Modello errori (fondamentale)

### Cosa diventa `Failure`

Nella variante functional, diventano `Failure` principalmente i casi di dominio/consistenza:
- save in stato inconsistente (`SaveAction.ERROR`)
- optimistic locking in update (catturata e convertita)
- delete(entity) con ID null

### Cosa continua a essere eccezione

`CoreRepositoryOperations` traduce `SQLException` in `DataAccessException`.
Questi errori infrastrutturali non vengono trasformati in `RepositoryResult.Failure` e continuano a propagare come eccezioni Spring.

Questo e voluto: separa edge case di business da failure infrastrutturali.

## SaveDecisionResolver: logica decisionale inserimento/aggiornamento

`SaveDecisionResolver<T,ID>` applica questa logica:
1. Se entity implementa `Persistable`:
- `isNew() == true` -> insert (provided o auto in base a strategia ID)
- `isNew() == false` -> update
2. Altrimenti:
- ID null -> insert
- ID non null + existsById true -> update
- ID non null + existsById false:
- strategy PROVIDED -> insert with provided id
- strategy IDENTITY -> `ERROR`

Questo evita ambiguita e gestisce in modo esplicito il caso inconsistente di IDENTITY con ID impostato ma inesistente a DB.

## Mutual exclusivity con repository Spring standard

Le interfacce functional sono volutamente mutualmente esclusive con `CrudRepository`/`PagingAndSortingRepository` standard.

Motivo: molti metodi hanno stessa firma parametri ma return type differente; in Java non e possibile combinarli nella stessa gerarchia.

## Multi-datasource e precedenze configurazione

Da annotation + factory bean + wiki emerge questo modello:

- `connectionProviderRef` prevale su `dataSourceRef`
- `dslRef` prevale su auto-detection dialetto
- `dslRegistryRef` serve per risoluzione DSL quando si usa `dataSourceRef`
- in assenza di ref espliciti:
  - bean unico o `@Primary` viene usato
  - setup ambiguo fallisce fast con messaggio chiaro

## Query derivate in modalita functional

Le query derivate via PartTree sono disponibili anche con return type functional.
Esempi testati in integrazione:
- `findByEmail` -> `RepositoryResult<Optional<User>>`
- `findByName` -> `RepositoryResult<List<User>>`
- `countByActive` -> `RepositoryResult<Long>`
- `existsByEmail` -> `RepositoryResult<Boolean>`

La strategia runtime e cacheata per method (`FluentQueryLookupStrategy`).

## Evidenze dai test

### Unit

- `RepositoryResultTest`: invarianti e combinatori
- `FunctionalFluentRepositoryTest`: branch di save, stop su prima failure in saveAll, delete null-id, paging edge cases, `markPersisted`
- `FluentRepositoryFactoryBeanTest`: error handling su bean resolution (missing/ambiguous)
- `FluentRepositoryFactoryFragmentTest`: injection contesto frammenti custom e guard rail multi-datasource

### Integration

`FunctionalUserRepositoryIntegrationTest` verifica con H2:
- registrazione automatica repository
- flussi CRUD completi con wrapper `RepositoryResult`
- paging/sorting
- query derivate functional

## Differenza pratica rispetto alla variante standard

`FluentRepository` (standard) usa la stessa infrastruttura core ma:
- ritorna tipi Spring classici
- in piu casi usa eccezioni invece di result type espliciti
- delete API classica e void (meno feedback)

`FunctionalFluentRepository` mantiene lo stesso motore SQL ma espone API piu espressive lato chiamante.

## Limiti e note progettuali rilevanti

Dalla wiki tecnica:
- mapping flat (niente object graph ORM)
- no persistence context/first-level cache
- no join automatici per path annidati nelle query derivate
- alcune famiglie di operatori non supportate (es. regex/geospaziali)
- strategia ID sequence non ancora supportata (planned)

Questi limiti sono coerenti con il posizionamento del progetto: repository JDBC leggeri e prevedibili, non ORM.

## Conclusione

La parte functional di fluent-repo-4j e progettata come un layer semantico sopra al core JDBC condiviso:
- nessuna duplicazione del motore SQL
- differenziazione forte solo nel contratto API e gestione outcome
- integrazione totale con scanning/proxy/query derivation Spring Data

`RepositoryResult` rende il contratto repository piu esplicito e composabile, mantenendo separati:
- esiti di dominio gestibili dal chiamante (`Success`/`Failure`)
- errori infrastrutturali che restano eccezioni (`DataAccessException`)

Per servizi applicativi e casi in cui si vogliono evitare try/catch diffusi su conflitti e assenze, la variante functional fornisce un modello piu leggibile e robusto senza sacrificare le funzionalita core del progetto.
