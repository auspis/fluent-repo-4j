# Plan: ID Generation Support per fluent-repo-4j

## Overview

Implementare una strategia di ID generation che:
1. Supporta entità dove **l'applicazione fornisce l'ID** (Application-driven, NONE strategy)
2. Prepara l'architettura per aggiungere **database-generated ID** (IDENTITY, SEQUENCE) in futuro
3. Mantiene massima flessibilità con supporto per `Persistable<ID>` di Spring Data

---

## Opzioni Analizzate

### Opzione 1: Flag `@Transient` per `isNew()` ❌ SCARTATA

**Proposta**: Aggiungere campo `@Transient boolean isNew` che viene impostato a `false` quando letto dal DB.

**Problemi critici**:
- ❌ **Serialization breaking**: `@Transient` non sopravvive JSON/XML roundtrip
- ❌ **Thread-safety**: Flag mutabile condiviso tra thread
- ❌ **Clone/copy issues**: Facile perdere traccia dello stato (creare clone di entity → `isNew` ritorna `true` ma l'ID esiste nel DB!)
- ❌ **Non-standard**: Nessun altro modulo Spring Data lo fa così
- ❌ **Intrusivo**: Boilerplate in OGNI entity

---

### Opzione 2: Alternativa Minimalista (ID null + setId) ✅ SEMPLICE MA LIMITATA

```java
User user = new User("Alice", "alice@example.com", 30);
user.setId(generateId());  // UUID, sequenza custom, etc.
repository.save(user);
```

**Pro**:
- ✅ Zero boilerplate in entity
- ✅ Compatibile con `isNew()` standard Spring Data
- ✅ No thread-safety issues
- ✅ No serialization issues

**Contro**:
- ❌ Richiede 2 step (costruzione + setId)
- ❌ Non ergonomico per UUID client-side (quando il dev vuole passare UUID nel constructor)
- ❌ **Fallback implicito**: Se dimentichiamo `@GeneratedValue`, cosa succede?

---

### Opzione 3: `Persistable<ID>` (Standard Spring Data) ✅ FLESSIBILE

```java
@Table(name = "events")
public class Event implements Persistable<UUID> {
    @Id
    private UUID id;
    
    @Transient
    private boolean isNew = true;
    
    @Override
    public UUID getId() { return id; }
    
    @Override
    public boolean isNew() { return isNew; }
    
    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }
}
```

**Pro**:
- ✅ Pattern standard Spring Data (documentato)
- ✅ Funziona perfettamente con UUID
- ✅ Esplicito: il dev sa cosa sta facendo
- ✅ Flessibilità totale

**Contro**:
- ❌ Richiede `implements Persistable<ID>` (intrusivo)
- ❌ Boilerplate (ma solo per entity che lo necessitano)
- ❌ Dev deve aggiungere callback `@PostLoad/@PostPersist`

---

## Decisione Finale: IBRIDA (Opzione 3 + Fallback Automatico)

### Strategia Implementativa

**Se entity implementa `Persistable<ID>`** → Usa `entity.isNew()` (controllo totale dev)

```java
@Override
public boolean isNew(T entity) {
    // If entity implements Persistable<ID>, delegate to its isNew() method
    if (entity instanceof org.springframework.data.domain.Persistable<?> persistable) {
        return persistable.isNew();
    }
    
    // Otherwise: use standard Spring Data logic (ID == null → isNew = true)
    return super.isNew(entity);
}
```

**Se entity NON implementa `Persistable<ID>`** → Fallback a `getId() == null`

Questo offre:
- ✅ **Libertà totale**: Dev può implementare `Persistable<ID>` per casi complessi (UUID, business keys)
- ✅ **Zero boilerplate di default**: Entity semplici continuano a funzionare con ID null
- ✅ **Standard Spring Data**: `Persistable<ID>` è l'interface ufficiale
- ✅ **No breaking changes**: Tutto è opt-in

---

## Implementazione MVP: Application-provided ID (NONE Strategy)

### Cosa NON Implementare (per ora)

- ❌ `@GeneratedValue(IDENTITY)` → Richiede `getGeneratedKeys()`, dialect detection
- ❌ `@GeneratedValue(SEQUENCE)` → Richiede query separata `SELECT nextval()`
- ❌ Annotation `@IdGenerationStrategy` separata → Troppo boilerplate

### Cosa Implementare

**Step 1: Override `isNew()` in `FluentEntityInformation`**

File: `/home/massi/dev/fluent-repo-4j/src/main/java/io/github/auspis/fluentrepo4j/mapping/FluentEntityInformation.java`

Aggiungere metodo dopo `getIdType()`:

```java
@Override
public boolean isNew(T entity) {
    // If entity implements Persistable<ID>, delegate to its isNew() method
    if (entity instanceof org.springframework.data.domain.Persistable<?> persistable) {
        return persistable.isNew();
    }
    
    // Otherwise, use standard Spring Data logic: ID == null → isNew = true
    return super.isNew(entity);
}
```

**Step 2: Modificare `SimpleFluentRepository.insert()`**

File: `/home/massi/dev/fluent-repo-4j/src/main/java/io/github/auspis/fluentrepo4j/repository/SimpleFluentRepository.java`

Rimuovere questa riga:
```java
values.put("id", "100023");  // ← RIMUOVERE!
```

Aggiungere validazione:
```java
ID idValue = entityInformation.getId(entity);
if (idValue == null) {
    throw new IllegalArgumentException(
        "Entity " + entity.getClass().getSimpleName() + " requires ID to be set before save(). " +
        "No @GeneratedValue found; use application-provided ID strategy."
    );
}
```

Usare `getAllColumnValues(entity)` che include l'ID:
```java
Map<String, Object> values = entityWriter.getAllColumnValues(entity);
```

**Step 3: Aggiornare Test**

File: `/home/massi/dev/fluent-repo-4j/src/test/java/io/github/auspis/fluentrepo4j/repository/SimpleFluentRepositoryIT.java`

Il test deve impostare l'ID **prima** di `save()`:

```java
@Test
void save_insertNewEntity() {
    User user = new User("Alice", "alice@example.com", 30);
    user.setId(100L);  // ← APP DEVE IMPOSTARE ID
    
    long count = repository.count();
    User saved = repository.save(user);

    assertThat(saved).isSameAs(user);
    assertThat(saved.getId()).isEqualTo(100L);
    assertThat(repository.count()).isEqualTo(count + 1);
}
```

---

## Flusso Logico: isNew() → insert/update

```
repository.save(user)
    ↓
entityInformation.isNew(user)
    ↓
┌─────────────────────────────────────────────────────────┐
│ If user instanceof Persistable<ID>                       │
│   → return user.isNew()  (developer has full control)    │
│ Else                                                      │
│   → return super.isNew(user)  (ID == null → true)       │
└─────────────────────────────────────────────────────────┘
    ↓
┌──────────┬──────────┐
│ true     │ false    │
│ (nuovo)  │ (esiste) │
└──────────┴──────────┘
│          │
insert()   update()
```

---

## Casi d'Uso Supportati da MVP

### Caso 1: Entity Semplice (NO Persistable)
```java
@Table(name = "users")
public class User {
    @Id
    private Long id;
    private String name;
    private String email;
    // ...
}

User user = new User("Alice", "alice@example.com", 30);
user.setId(1L);  // APP MUST SET ID BEFORE SAVE
repository.save(user);  // → insert() con id=1
```

**Logica**: `getId() != null` e non implementa `Persistable` → `isNew()` fallback a `super.isNew()` → `false` (perché ID != null)

⚠️ **NOTA**: Questo è un **PROBLEMA**! Se dev imposta l'ID su entity nuova (non ancora nel DB), `isNew()` ritornerà `false` → UPDATE fallisce silenziosamente!

**Soluzione**: Dev DEVE creare entity con `id=null`, poi impostare ID prima di save:
```java
User user = new User("Alice", "alice@example.com", 30);  // id = null
user.setId(1L);  // ← NOW getId() != null, BUT isNew() was already called!
```

⚠️ **MEGLIO**: Usare Persistable per controllo esplicito:

### Caso 2: Entity con Persistable (YES)
```java
@Table(name = "events")
public class Event implements Persistable<UUID> {
    @Id
    private UUID id;
    
    @Transient
    private boolean isNew = true;
    
    public Event() {
        this.id = UUID.randomUUID();
    }
    
    @Override
    public boolean isNew() { return isNew; }
    
    @Override
    public UUID getId() { return id; }
    
    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }
}

Event event = new Event();  // uuid già generato, isNew=true
repository.save(event);  // → isNew() ritorna true → insert()
```

**Logica**: Implementa `Persistable` → `isNew()` chiama `event.isNew()` → `true` → INSERT ✓

---

## Quando Aggiungere @GeneratedValue+IDENTITY (v1.1)

Una volta MVP stabile, aggiungere supporto IDENTITY:

1. **Estendere `FluentEntityInformation`** per parsing `@GeneratedValue` annotation
2. **Creare enum `IdGenerationStrategy`** con `{PROVIDED, IDENTITY, SEQUENCE, ...}`
3. **Modificare `insert()`** per:
   - Se IDENTITY: escludere ID dalla INSERT, eseguire con `Statement.RETURN_GENERATED_KEYS`, popolare ID via reflection
   - Se PROVIDED: includere ID nella INSERT
4. **Aggiungere test** per entrambe le strategie

---

## Decisione Finale: Quale Approccio?

### Opzione A: Annotation Separata `@IdGenerationStrategy`

```java
@Id
@IdGenerationStrategy(Strategy.PROVIDED)  // ← Obbligatorio
private UUID id;
```

**Pro**: Esplicito, validation compile-time, non inquina JPA  
**Contro**: Boilerplate extra

### Opzione B: Estendere JPA `@GeneratedValue`

```java
@Id
@GeneratedValue(strategy = GenerationType.AUTO)  // Interpretiamo come PROVIDED
private UUID id;
```

**Pro**: Standard JPA, meno boilerplate  
**Contro**: Semantic ambiguo (AUTO ha già significato in JPA)

### Opzione C: Zero Annotation (Fallback Default)

```java
@Id  // Nessuna @GeneratedValue
private UUID id;  // Default: PROVIDED
```

**Pro**: Minimalista, zero boilerplate  
**Contro**: Implicito, no validation

---

## ⭐ RACCOMANDAZIONE FINALE

**Per MVP**:
1. **Implementare solo Persistable support** (override `isNew()`)
2. **Modificare `insert()` per rimuovere ID hardcoded**
3. **Zero annotation nuove** (usare solo `@Id`)
4. **Documentare**: "Se implementi `Persistable<ID>`, hai controllo totale su `isNew()`"

**Per v1.1**:
- Aggiungere `@GeneratedValue(IDENTITY)` support se utenti lo richiedono
- A quel punto decidere tra Opzione A/B/C per annotation strategy

---

## Checklist Implementativa

- [ ] Step 1: Override `isNew()` in `FluentEntityInformation` per supportare `Persistable<ID>`
- [ ] Step 2: Modificare `SimpleFluentRepository.insert()` per rimuovere ID hardcoded
- [ ] Step 3: Validare che ID != null prima di INSERT
- [ ] Step 4: Aggiornare test `save_insertNewEntity()` per impostare ID
- [ ] Step 5: Aggiungere test per `Persistable<ID>` (opzionale per MVP)
- [ ] Step 6: Aggiungere commenti/documentazione sul flusso

---

## Riferimenti

- Spring Data Commons: `AbstractEntityInformation.isNew()`
- Spring Data: `Persistable<ID>` interface
- Jakarta Persistence: `@GeneratedValue` annotation
- fluent-repo-4j: `FluentEntityInformation`, `SimpleFluentRepository`
