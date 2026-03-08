package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.dialect.DialectDetector;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.test.domain.CartItem;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.test.util.TestDatabaseUtil;

/**
 * Integration test for {@link SimpleFluentRepository} using an in-memory H2 database.
 * Tests both PROVIDED (application-set ID) and IDENTITY (database-generated ID) strategies.
 */
class SimpleFluentRepositoryIT {

    /**
     * Tests for entities using PROVIDED ID strategy (User: no @GeneratedValue).
     * The application must set the ID before calling save().
     */
    @Nested
    class ProvidedIdStrategyIT {

        private Connection connection;
        private SimpleFluentRepository<User, Long> repository;

        @BeforeEach
        void setUp() throws SQLException {
            connection = TestDatabaseUtil.createH2Connection();
            TestDatabaseUtil.createUsersTable(connection);
            TestDatabaseUtil.insertSampleUsers(connection);
            DataSource dataSource = new SingleConnectionDataSource(connection, true);

            DSLRegistry registry = DSLRegistry.createWithServiceLoader();
            DSL dsl = DialectDetector.detect(dataSource, registry);
            FluentConnectionProvider connectionProvider = new FluentConnectionProvider(dataSource);
            FluentEntityInformation<User, Long> entityInfo = new FluentEntityInformation<>(User.class);

            repository = new SimpleFluentRepository<>(entityInfo, connectionProvider, dsl);
        }

        @AfterEach
        void tearDown() throws SQLException {
            TestDatabaseUtil.closeConnection(connection);
        }

        @Test
        void save_insertOk() {
            User user = new User("Alice", "alice@example.com", 30);
            user.setId(100L);
            long countBefore = repository.count();

            User saved = repository.save(user);

            assertThat(saved).isSameAs(user);
            assertThat(saved.getId()).isEqualTo(100L);
            assertThat(repository.count()).isEqualTo(countBefore + 1);

            Optional<User> found = repository.findById(100L);
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Alice");
            assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
            assertThat(found.get().getAge()).isEqualTo(30);
        }

        @Test
        void save_nullId_throwsIllegalArgument() {
            User user = new User("NoId", "noid@example.com", 25);
            // id is null, no @GeneratedValue → should throw

            assertThatThrownBy(() -> repository.save(user))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requires ID to be set before save()");
        }

        @Test
        void save_updateOk() {
            User user = repository.findById(1L).orElseThrow();
            user.setName("John Updated");
            user.setEmail("john.updated@example.com");

            repository.save(user);

            Optional<User> found = repository.findById(1L);
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("John Updated");
            assertThat(found.get().getEmail()).isEqualTo("john.updated@example.com");
        }

        @Test
        void save_insertByExistsByIdFallback() {
            // ID is non-null → isNew() returns false, but entity doesn't exist in DB
            // The existsById() fallback should detect this and perform INSERT
            User user = new User("Fallback", "fallback@example.com", 40);
            user.setId(999L);

            User saved = repository.save(user);

            assertThat(saved.getId()).isEqualTo(999L);
            assertThat(repository.existsById(999L)).isTrue();
        }

        @Test
        void save_updateByExistsByIdFallback() {
            // ID is non-null → isNew() returns false, and entity exists in DB
            // The existsById() fallback should detect this and perform UPDATE
            User user = new User("Fallback", "fallback@example.com", 40);
            user.setId(1L);
            
            User saved = repository.save(user);
            
            assertThat(saved.getId()).isEqualTo(1L);
            assertThat(repository.existsById(1L)).isTrue();
        }
    }

    /**
     * Tests for entities using IDENTITY ID strategy (Product: @GeneratedValue(strategy = IDENTITY)).
     * The database generates the ID via auto-increment.
     */
    @Nested
    class IdentityIdStrategyIT {

        private Connection connection;
        private SimpleFluentRepository<CartItem, Long> repository;

        @BeforeEach
        void setUp() throws SQLException {
            connection = TestDatabaseUtil.createH2Connection();
            TestDatabaseUtil.createCartItemsTable(connection);
            DataSource dataSource = new SingleConnectionDataSource(connection, true);

            DSLRegistry registry = DSLRegistry.createWithServiceLoader();
            DSL dsl = DialectDetector.detect(dataSource, registry);
            FluentConnectionProvider connectionProvider = new FluentConnectionProvider(dataSource);
            FluentEntityInformation<CartItem, Long> entityInfo = new FluentEntityInformation<>(CartItem.class);

            repository = new SimpleFluentRepository<>(entityInfo, connectionProvider, dsl);
        }

        @AfterEach
        void tearDown() throws SQLException {
            TestDatabaseUtil.closeConnection(connection);
        }

        @Test
        void save_insertOk() {
            CartItem product = new CartItem(1L, 2L, "Coffee Cup", 9.99, 1);

            CartItem saved = repository.save(product);

            assertThat(saved).isSameAs(product);
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getId()).isPositive();
            
            Optional<CartItem> found = repository.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getCartId()).isEqualTo(1L);
            assertThat(found.get().getProductId()).isEqualTo(2L);
            assertThat(found.get().getProductName()).isEqualTo("Coffee Cup");
            assertThat(found.get().getUnitPrice()).isEqualTo(9.99);
            assertThat(found.get().getQuantity()).isEqualTo(1);
        }

        @Test
        void save_multipleInserts() {
            CartItem p1 = new CartItem(1L, 2L, "Alpha", 20.0, 2);
            CartItem p2 = new CartItem(1L, 3L, "Beta", 30.0, 3);
            CartItem p3 = new CartItem(1L, 4L, "Gamma", 40.0, 4);

            repository.save(p1);
            repository.save(p2);
            repository.save(p3);

            assertThat(p1.getId()).isNotNull();
            assertThat(p2.getId()).isNotNull();
            assertThat(p3.getId()).isNotNull();
            assertThat(p1.getId()).isNotEqualTo(p2.getId());
            assertThat(p2.getId()).isNotEqualTo(p3.getId());
            assertThat(repository.count()).isEqualTo(3);
        }

        @Test
        void save_update() {
            CartItem product = new CartItem(1L, 2L, "Coffee Cup", 9.99, 1);
            repository.save(product);
            Long generatedId = product.getId();

            product.setQuantity(5);
            repository.save(product);

            Optional<CartItem> found = repository.findById(generatedId);
            assertThat(found).isPresent();
            assertThat(found.get().getQuantity()).isEqualTo(5);
        }
    }
    
//  @Test
//  void save_updateExistingEntity() throws Exception {
//      long id = insertRaw("Alice", "alice@example.com", 30);
//
//      User user = new User(id, "Alice Updated", "alice.updated@example.com", 31);
//      repository.save(user);
//
//      Optional<User> found = repository.findById(id);
//      assertThat(found).isPresent();
//      assertThat(found.get().getName()).isEqualTo("Alice Updated");
//      assertThat(found.get().getEmail()).isEqualTo("alice.updated@example.com");
//      assertThat(found.get().getAge()).isEqualTo(31);
//  }
//
//  @Test
//  void saveAll_multipleEntities() {
//      List<User> users = List.of(
//              new User(null, "Alice", "alice@example.com", 30),
//              new User(null, "Bob", "bob@example.com", 25)
//      );
//
//      Iterable<User> saved = repository.saveAll(users);
//
//      assertThat(saved).hasSize(2);
//      assertThat(repository.count()).isEqualTo(2);
//  }
//
//  // ---- Find ----
//
//  @Test
//  void findById_existing() throws Exception {
//      long id = insertRaw("Alice", "alice@example.com", 30);
//
//      Optional<User> found = repository.findById(id);
//
//      assertThat(found).isPresent();
//      assertThat(found.get().getName()).isEqualTo("Alice");
//      assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
//      assertThat(found.get().getAge()).isEqualTo(30);
//  }
//
//  @Test
//  void findById_nonExisting() {
//      Optional<User> found = repository.findById(999L);
//      assertThat(found).isEmpty();
//  }
//
//  @Test
//  void findAll_returnsAllEntities() throws Exception {
//      insertRaw("Alice", "alice@example.com", 30);
//      insertRaw("Bob", "bob@example.com", 25);
//
//      Iterable<User> all = repository.findAll();
//
//      assertThat(all).hasSize(2);
//  }
//
//  @Test
//  void findAllById_returnsMatching() throws Exception {
//      long id1 = insertRaw("Alice", "alice@example.com", 30);
//      insertRaw("Bob", "bob@example.com", 25);
//      long id3 = insertRaw("Charlie", "charlie@example.com", 35);
//
//      Iterable<User> found = repository.findAllById(List.of(id1, id3));
//
//      List<String> names = StreamSupport.stream(found.spliterator(), false)
//              .map(User::getName)
//              .toList();
//      assertThat(names).containsExactlyInAnyOrder("Alice", "Charlie");
//  }
//
//  // ---- Exists / Count ----
//
//  @Test
//  void existsById_true() throws Exception {
//      long id = insertRaw("Alice", "alice@example.com", 30);
//      assertThat(repository.existsById(id)).isTrue();
//  }
//
//  @Test
//  void existsById_false() {
//      assertThat(repository.existsById(999L)).isFalse();
//  }
//
//  @Test
//  void count_emptyTable() {
//      assertThat(repository.count()).isEqualTo(0);
//  }
//
//  @Test
//  void count_withData() throws Exception {
//      insertRaw("Alice", "alice@example.com", 30);
//      insertRaw("Bob", "bob@example.com", 25);
//      assertThat(repository.count()).isEqualTo(2);
//  }
//
//  // ---- Delete ----
//
//  @Test
//  void deleteById_removesEntity() throws Exception {
//      long id = insertRaw("Alice", "alice@example.com", 30);
//
//      repository.deleteById(id);
//
//      assertThat(repository.existsById(id)).isFalse();
//      assertThat(repository.count()).isEqualTo(0);
//  }
//
//  @Test
//  void delete_byEntity() throws Exception {
//      long id = insertRaw("Alice", "alice@example.com", 30);
//      User user = new User(id, "Alice", "alice@example.com", 30);
//
//      repository.delete(user);
//
//      assertThat(repository.count()).isEqualTo(0);
//  }
//
//  @Test
//  void deleteAllById_removesSelected() throws Exception {
//      long id1 = insertRaw("Alice", "alice@example.com", 30);
//      long id2 = insertRaw("Bob", "bob@example.com", 25);
//      long id3 = insertRaw("Charlie", "charlie@example.com", 35);
//
//      repository.deleteAllById(List.of(id1, id3));
//
//      assertThat(repository.count()).isEqualTo(1);
//      assertThat(repository.existsById(id2)).isTrue();
//  }
//
//  @Test
//  void deleteAll_removesEverything() throws Exception {
//      insertRaw("Alice", "alice@example.com", 30);
//      insertRaw("Bob", "bob@example.com", 25);
//
//      repository.deleteAll();
//
//      assertThat(repository.count()).isEqualTo(0);
//  }
//
//  @Test
//  void deleteAll_withEntities() throws Exception {
//      long id1 = insertRaw("Alice", "alice@example.com", 30);
//      long id2 = insertRaw("Bob", "bob@example.com", 25);
//      long id3 = insertRaw("Charlie", "charlie@example.com", 35);
//
//      User alice = new User(id1, "Alice", "alice@example.com", 30);
//      User bob = new User(id2, "Bob", "bob@example.com", 25);
//
//      repository.deleteAll(List.of(alice, bob));
//
//      assertThat(repository.count()).isEqualTo(1);
//      assertThat(repository.existsById(id3)).isTrue();
//  }
//
//  // ---- Null handling ----
//
//  @Test
//  void save_entityWithNullFields() {
//      User user = new User(null, "Alice", null, null);
//      repository.save(user);
//
//      Iterable<User> all = repository.findAll();
//      assertThat(all).hasSize(1);
//      User found = all.iterator().next();
//      assertThat(found.getName()).isEqualTo("Alice");
//      assertThat(found.getEmail()).isNull();
//      assertThat(found.getAge()).isNull();
//  }
}
