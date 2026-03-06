## Plan: Spring Data Fluent-SQL-4J Module

Modulo Spring Data custom basato su fluent-sql-4j per accesso JDBC. Dipende da spring-data-commons e spring-jdbc (senza dipendere da spring-data-jdbc). Supporta CrudRepository con CRUD base, mapping semplice tabella→oggetto e integrazione trasparente con transazioni Spring tramite DataSourceUtils. Non è un ORM: niente oggetti annidati/graph loading automatico; le relazioni si gestiscono con repository separati e query esplicite.

**Dipendenze Maven del modulo**
- spring-data-commons (Repository SPI, RepositoryMetadata, EntityInformation)
- spring-jdbc (DataSourceUtils, RowMapper, SQLExceptionTranslator)
- fluent-sql-4j:api + plugin dialect desiderato

**Steps**
1. Bootstrap Spring Data repository:
- EnableFluentRepositories
- FluentRepositoriesRegistrar
- FluentRepositoryConfigExtension
- FluentRepositoryFactoryBean (extends TransactionalRepositoryFactoryBeanSupport)
- FluentRepositoryFactory (extends RepositoryFactorySupport, usa pipeline proxy standard Spring Data)

2. Connection e transazioni:
- FluentConnectionProvider con DataSourceUtils.getConnection/releaseConnection
- Se esiste @Transactional: aggancio alla connection transazionale
- Se non esiste: fallback auto-commit
- Dialect detection da DatabaseMetaData con fallback configurazione esplicita

3. Repository base CRUD:
- FluentEntityInformation<T, ID>
- SimpleFluentRepository<T, ID> con save, findById, findAll, deleteById, count
- Scope corrente: niente PartTree e niente FluentQuery annotation

4. Mapping semplice ResultSet → oggetto:
- FluentEntityRowMapper<T>
- FluentEntityWriter<T>
- Annotazioni mapping: riuso jakarta.persistence per Table/Column e Id
- Mapping piatto: nessun caricamento automatico di oggetti annidati

5. AutoConfiguration e robustezza:
- FluentRepositoriesAutoConfiguration
- Exception translation con SQLExceptionTranslator → DataAccessException
- Supporto custom fragments (interfaccia + Impl) con accesso DSL/DataSource

**Verification**
- Integration test H2: CRUD + mapping base
- Test transazionale: @Transactional e riuso connection
- Test non transazionale: fallback auto-commit
- Test bootstrap: EnableFluentRepositories registra e crea i repository bean
- Test mapping annotation: Table/Column/Id risolti correttamente

**Decisioni correnti**
- EnableFluentRepositories + AutoConfiguration come entrypoint
- Factory pattern Spring Data (RepositoryFactoryBeanSupport/RepositoryFactorySupport), non ProxyFactoryBean diretto
- DataSourceUtils per integrazione transazionale
- RowMapper come meccanismo base di mapping
- Mapping semplice non-ORM
- Annotazioni JPA per Table/Column
- PartTree e FluentQuery fuori dallo scope corrente
