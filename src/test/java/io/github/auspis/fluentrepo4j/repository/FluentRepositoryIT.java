package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.dialect.DialectDetector;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.test.domain.CartItem;
import io.github.auspis.fluentrepo4j.test.domain.Product;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest;
import io.github.auspis.fluentsql4j.test.util.database.TestDatabaseUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Integration test for {@link FluentRepository} using an in-memory H2 database.
 * Tests both PROVIDED (application-set ID) and IDENTITY (database-generated ID) strategies.
 */
@IntegrationTest
class FluentRepositoryIT {

    /**
     * Tests for entities using PROVIDED ID strategy (User: no @GeneratedValue).
     * The application must set the ID before calling save().
     */
    @Nested
    class ProvidedIdStrategy {

        private Connection connection;
        private FluentRepository<User, Long> repository;

        @BeforeEach
        void setUp() throws SQLException {
            connection = TestDatabaseUtil.H2.createConnection();
            TestDatabaseUtil.H2.createUsersTable(connection);
            TestDatabaseUtil.H2.insertSampleUsers(connection);
            DataSource dataSource = new SingleConnectionDataSource(connection, true);

            DSLRegistry registry = DSLRegistry.createWithServiceLoader();
            DSL dsl = DialectDetector.detect(dataSource, registry);
            FluentConnectionProvider connectionProvider = new FluentConnectionProvider(dataSource);
            FluentEntityInformation<User, Long> entityInfo = new FluentEntityInformation<>(User.class);

            repository = new FluentRepository<>(new CoreRepositoryOperations<>(entityInfo, connectionProvider, dsl));
        }

        @AfterEach
        void tearDown() throws SQLException {
            TestDatabaseUtil.H2.closeConnection(connection);
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

        @Test
        void findAllById_returnsMatching() {
            Iterable<User> found = repository.findAllById(List.of(1L, 3L));

            List<String> names = new ArrayList<>();
            found.forEach(u -> names.add(u.getName()));
            assertThat(names).containsExactlyInAnyOrder("John Doe", "Bob");
        }

        @Test
        void findAllById_ignoresNonExisting() {
            Iterable<User> found = repository.findAllById(List.of(1L, 999L));

            List<String> names = new ArrayList<>();
            found.forEach(u -> names.add(u.getName()));
            assertThat(names).containsExactly("John Doe");
        }

        @Test
        void saveAll_insertsMultiple() {
            long countBefore = repository.count();
            User u1 = new User("New1", "new1@example.com", 30);
            u1.setId(100L);
            User u2 = new User("New2", "new2@example.com", 35);
            u2.setId(101L);

            Iterable<User> saved = repository.saveAll(List.of(u1, u2));

            assertThat(saved).hasSize(2);
            assertThat(repository.count()).isEqualTo(countBefore + 2);
        }

        @Test
        void delete_byEntity() {
            User user = repository.findById(1L).orElseThrow();

            repository.delete(user);

            assertThat(repository.existsById(1L)).isFalse();
        }

        @Test
        void deleteById_removesEntity() {
            long countBefore = repository.count();

            repository.deleteById(1L);

            assertThat(repository.existsById(1L)).isFalse();
            assertThat(repository.count()).isEqualTo(countBefore - 1);
        }

        @Test
        void delete_nullId_throwsIllegalArgument() {
            User user = new User("NoId", "noid@example.com", 25);

            assertThatThrownBy(() -> repository.delete(user))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot delete entity with null ID");
        }

        @Test
        void deleteAllById_removesSelected() {
            long countBefore = repository.count();
            repository.deleteAllById(List.of(1L, 3L));

            assertThat(repository.count()).isEqualTo(countBefore - 2);
            assertThat(repository.existsById(2L)).isTrue();
        }

        @Test
        void deleteAll_withEntities() {
            long countBefore = repository.count();
            User john = repository.findById(1L).orElseThrow();
            User jane = repository.findById(2L).orElseThrow();

            repository.deleteAll(List.of(john, jane));

            assertThat(repository.count()).isEqualTo(countBefore - 2);
            assertThat(repository.existsById(3L)).isTrue();
        }

        @Test
        void deleteAll_removesEverything() {
            repository.deleteAll();

            assertThat(repository.count()).isZero();
        }

        @Test
        void count_emptyTable() throws SQLException {
            TestDatabaseUtil.H2.truncateUsers(connection);

            assertThat(repository.count()).isZero();
        }

        @Test
        void save_entityWithNullFields() {
            User user = new User("Alice", "placeholder@example.com", 30);
            user.setId(100L);
            user.setEmail(null);
            user.setAge(null);

            repository.save(user);

            Optional<User> found = repository.findById(100L);
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Alice");
            assertThat(found.get().getEmail()).isNull();
            assertThat(found.get().getAge()).isNull();
        }
    }

    /**
     * Tests for entities using IDENTITY ID strategy (Product: @GeneratedValue(strategy = IDENTITY)).
     * The database generates the ID via auto-increment.
     */
    @Nested
    class IdentityIdStrategy {

        private Connection connection;
        private FluentRepository<CartItem, Long> repository;

        @BeforeEach
        void setUp() throws SQLException {
            connection = TestDatabaseUtil.H2.createConnection();
            TestDatabaseUtil.H2.createCartItemsTable(connection);
            DataSource dataSource = new SingleConnectionDataSource(connection, true);

            DSLRegistry registry = DSLRegistry.createWithServiceLoader();
            DSL dsl = DialectDetector.detect(dataSource, registry);
            FluentConnectionProvider connectionProvider = new FluentConnectionProvider(dataSource);
            FluentEntityInformation<CartItem, Long> entityInfo = new FluentEntityInformation<>(CartItem.class);

            repository = new FluentRepository<>(new CoreRepositoryOperations<>(entityInfo, connectionProvider, dsl));
        }

        @AfterEach
        void tearDown() throws SQLException {
            TestDatabaseUtil.H2.closeConnection(connection);
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

        @Test
        void save_identityWithNonNullIdNotInDb_throwsIllegalState() {
            // An IDENTITY entity with a manually set ID that doesn't exist in DB is inconsistent
            CartItem item = new CartItem(9999L, 1L, 2L, "Ghost", 1.0, 1);

            assertThatThrownBy(() -> repository.save(item))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("IDENTITY strategy");
        }
    }

    /**
     * Tests for entities implementing {@link org.springframework.data.domain.Persistable}.
     * The entity controls {@code isNew()} — no database check is performed by the resolver.
     */
    @Nested
    class Persistable {

        private Connection connection;
        private FluentRepository<Product, Integer> repository;

        @BeforeEach
        void setUp() throws SQLException {
            connection = TestDatabaseUtil.H2.createConnection();
            TestDatabaseUtil.H2.createProductsTable(connection);
            DataSource dataSource = new SingleConnectionDataSource(connection, true);

            DSLRegistry registry = DSLRegistry.createWithServiceLoader();
            DSL dsl = DialectDetector.detect(dataSource, registry);
            FluentConnectionProvider connectionProvider = new FluentConnectionProvider(dataSource);
            FluentEntityInformation<Product, Integer> entityInfo = new FluentEntityInformation<>(Product.class);

            repository = new FluentRepository<>(new CoreRepositoryOperations<>(entityInfo, connectionProvider, dsl));
        }

        @AfterEach
        void tearDown() throws SQLException {
            TestDatabaseUtil.H2.closeConnection(connection);
        }

        @Test
        void save_isNewTrue_insertsEntity() {
            Product product = new Product(100, "TestWidget", 29.99, 50);
            assertThat(product.isNew()).isTrue();

            Product saved = repository.save(product);

            assertThat(saved).isSameAs(product);
            assertThat(saved.getId()).isEqualTo(100);
            assertThat(saved.isNew()).isFalse(); // library auto-called markPersisted()
            assertThat(repository.existsById(100)).isTrue();

            Optional<Product> found = repository.findById(100);
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("TestWidget");
            assertThat(found.get().getPrice()).isEqualTo(29.99);
            assertThat(found.get().isNew()).isFalse(); // library auto-called markPersisted() on load
        }

        @Test
        void save_isNewFalse_updatesEntity() {
            Product product = new Product(100, "TestWidget", 29.99, 50);
            repository.save(product); // auto-calls markPersisted()

            // no manual markPersisted() needed
            product.setName("UpdatedWidget");
            product.setPrice(39.99);
            repository.save(product);

            Optional<Product> found = repository.findById(100);
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("UpdatedWidget");
            assertThat(found.get().getPrice()).isEqualTo(39.99);
        }

        @Test
        void save_isNewFalse_notInDb_throwsOptimisticLocking() {
            // Construct entity already marked as persisted (isNew = false)
            // but the row doesn't exist in DB → UPDATE will find 0 rows
            Product product = new Product(9999, "Ghost", 0.0, 0, null, false);

            assertThatThrownBy(() -> repository.save(product))
                    .isInstanceOf(OptimisticLockingFailureException.class)
                    .hasMessageContaining("was not found for update");
        }
    }
}
