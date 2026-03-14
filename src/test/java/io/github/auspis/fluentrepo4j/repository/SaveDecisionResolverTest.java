package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.test.domain.CartItem;
import io.github.auspis.fluentrepo4j.test.domain.Product;
import io.github.auspis.fluentrepo4j.test.domain.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SaveDecisionResolver}.
 * Uses real {@link FluentEntityInformation} (annotation-driven metadata)
 * and a simple lambda for the {@code existsById} predicate — no Spring context needed.
 */
class SaveDecisionResolverTest {

    // ---- PROVIDED strategy (User: no @GeneratedValue) ----

    @Nested
    class ProvidedStrategy {

        private final FluentEntityInformation<User, Long> entityInfo = new FluentEntityInformation<>(User.class);

        @Test
        void nullId_returnsInsertProvidedId() {
            var resolver = new SaveDecisionResolver<>(entityInfo, id -> false);
            User user = new User("Alice", "alice@example.com", 30);

            assertThat(resolver.apply(user)).isEqualTo(SaveAction.INSERT_PROVIDED_ID);
        }

        @Test
        void nonNullId_existsInDb_returnsUpdate() {
            var resolver = new SaveDecisionResolver<>(entityInfo, id -> true);
            User user = new User("Alice", "alice@example.com", 30);
            user.setId(1L);

            assertThat(resolver.apply(user)).isEqualTo(SaveAction.UPDATE);
        }

        @Test
        void nonNullId_notInDb_returnsInsertProvidedId() {
            var resolver = new SaveDecisionResolver<>(entityInfo, id -> false);
            User user = new User("Alice", "alice@example.com", 30);
            user.setId(999L);

            assertThat(resolver.apply(user)).isEqualTo(SaveAction.INSERT_PROVIDED_ID);
        }
    }

    // ---- IDENTITY strategy (CartItem: @GeneratedValue(IDENTITY)) ----

    @Nested
    class IdentityStrategy {

        private final FluentEntityInformation<CartItem, Long> entityInfo =
                new FluentEntityInformation<>(CartItem.class);

        @Test
        void nullId_returnsInsertAutoId() {
            var resolver = new SaveDecisionResolver<>(entityInfo, id -> false);
            CartItem item = new CartItem(1L, 2L, "Widget", 9.99, 1);

            assertThat(resolver.apply(item)).isEqualTo(SaveAction.INSERT_AUTO_ID);
        }

        @Test
        void nonNullId_existsInDb_returnsUpdate() {
            var resolver = new SaveDecisionResolver<>(entityInfo, id -> true);
            CartItem item = new CartItem(1L, 1L, 2L, "Widget", 9.99, 1);

            assertThat(resolver.apply(item)).isEqualTo(SaveAction.UPDATE);
        }

        @Test
        void nonNullId_notInDb_returnsError() {
            var resolver = new SaveDecisionResolver<>(entityInfo, id -> false);
            CartItem item = new CartItem(99L, 1L, 2L, "Widget", 9.99, 1);

            assertThat(resolver.apply(item)).isEqualTo(SaveAction.ERROR);
        }
    }

    // ---- Persistable (Product: implements Persistable<Integer>) ----

    @Nested
    class PersistableStrategy {

        private final FluentEntityInformation<Product, Integer> entityInfo =
                new FluentEntityInformation<>(Product.class);

        @Test
        void isNew_true_returnsInsertProvidedId() {
            // existsById should NOT be called — Persistable path skips DB
            var resolver = new SaveDecisionResolver<>(entityInfo, id -> {
                throw new AssertionError("existsById should not be called for Persistable");
            });
            Product product = new Product(1, "Widget", 19.99, 100);

            assertThat(resolver.apply(product)).isEqualTo(SaveAction.INSERT_PROVIDED_ID);
        }

        @Test
        void isNew_false_returnsUpdate() {
            // existsById should NOT be called — Persistable path skips DB
            var resolver = new SaveDecisionResolver<>(entityInfo, id -> {
                throw new AssertionError("existsById should not be called for Persistable");
            });
            // Construct with isNewEntity = false directly (simulates post-save state)
            Product product = new Product(1, "Widget", 19.99, 100, null, false);

            assertThat(resolver.apply(product)).isEqualTo(SaveAction.UPDATE);
        }
    }
}
