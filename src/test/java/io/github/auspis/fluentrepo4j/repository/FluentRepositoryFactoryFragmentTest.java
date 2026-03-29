package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContext;
import io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContextAware;
import io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContextFactory;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.data.repository.core.support.RepositoryFragment;

/**
 * Unit test verifying that {@link FluentRepositoryFactory} injects
 * {@link FluentRepositoryContext} into {@link FluentRepositoryContextAware}
 * fragment implementations when building the repository proxy, including
 * the singleton overwrite guard.
 */
class FluentRepositoryFactoryFragmentTest {

    private final DSL dsl = Mockito.mock(DSL.class);
    private final FluentConnectionProvider connectionProvider = Mockito.mock(FluentConnectionProvider.class);
    private final FluentRepositoryFactory factory = new FluentRepositoryFactory(connectionProvider, dsl);

    @Test
    void awareFragment() {
        AwareFragment aware = new AwareFragment();
        RepositoryFragments fragments = RepositoryFragments.of(RepositoryFragment.implemented(aware));
        FluentRepositoryContext<User> context =
                FluentRepositoryContextFactory.create(dsl, connectionProvider, User.class);

        factory.injectFluentContext(fragments, context);

        assertThat(aware.context).isSameAs(context);
        assertThat(aware.context.dsl()).isSameAs(dsl);
        assertThat(aware.context.connectionProvider()).isSameAs(connectionProvider);
        assertThat(aware.context.rowMapper()).isNotNull();
        assertThat(aware.context.writer()).isNotNull();
    }

    @Test
    void nonAwareFragment() {
        NonAwareFragment nonAware = Mockito.mock(NonAwareFragment.class);
        RepositoryFragments fragments = RepositoryFragments.of(RepositoryFragment.implemented(nonAware));
        FluentRepositoryContext<User> context =
                FluentRepositoryContextFactory.create(dsl, connectionProvider, User.class);

        factory.injectFluentContext(fragments, context);

        Mockito.verifyNoInteractions(nonAware);
    }

    @Test
    void mixedFragments() {
        AwareFragment aware = new AwareFragment();
        NonAwareFragment nonAware = Mockito.mock(NonAwareFragment.class);
        RepositoryFragments fragments =
                RepositoryFragments.of(RepositoryFragment.implemented(aware), RepositoryFragment.implemented(nonAware));
        FluentRepositoryContext<User> context =
                FluentRepositoryContextFactory.create(dsl, connectionProvider, User.class);

        factory.injectFluentContext(fragments, context);

        assertThat(aware.context).isSameAs(context);
        Mockito.verifyNoInteractions(nonAware);
    }

    @Test
    void emptyFragments() {
        RepositoryFragments fragments = RepositoryFragments.empty();
        FluentRepositoryContext<User> context =
                FluentRepositoryContextFactory.create(dsl, connectionProvider, User.class);

        assertDoesNotThrow(() -> factory.injectFluentContext(fragments, context));
    }

    @Test
    void idempotentInjection() {
        AwareFragment aware = new AwareFragment();
        RepositoryFragments fragments = RepositoryFragments.of(RepositoryFragment.implemented(aware));
        FluentRepositoryContext<User> context =
                FluentRepositoryContextFactory.create(dsl, connectionProvider, User.class);

        factory.injectFluentContext(fragments, context);
        assertDoesNotThrow(() -> factory.injectFluentContext(fragments, context));

        assertThat(aware.context).isSameAs(context);
    }

    @Test
    void singletonOverwriteDifferentDsl() {
        AwareFragment aware = new AwareFragment();
        RepositoryFragments fragments = RepositoryFragments.of(RepositoryFragment.implemented(aware));

        FluentRepositoryContext<User> context1 =
                FluentRepositoryContextFactory.create(dsl, connectionProvider, User.class);
        factory.injectFluentContext(fragments, context1);

        DSL otherDsl = Mockito.mock(DSL.class);
        FluentRepositoryContext<User> context2 =
                FluentRepositoryContextFactory.create(otherDsl, connectionProvider, User.class);

        assertThatThrownBy(() -> factory.injectFluentContext(fragments, context2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shared across repository groups with different datasources");
    }

    @Test
    void singletonOverwriteDifferentConnectionProvider() {
        AwareFragment aware = new AwareFragment();
        RepositoryFragments fragments = RepositoryFragments.of(RepositoryFragment.implemented(aware));

        FluentRepositoryContext<User> context1 =
                FluentRepositoryContextFactory.create(dsl, connectionProvider, User.class);
        factory.injectFluentContext(fragments, context1);

        FluentConnectionProvider otherProvider = Mockito.mock(FluentConnectionProvider.class);
        FluentRepositoryContext<User> context2 = FluentRepositoryContextFactory.create(dsl, otherProvider, User.class);

        assertThatThrownBy(() -> factory.injectFluentContext(fragments, context2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shared across repository groups with different datasources");
    }

    // -- Test helpers --

    static class AwareFragment implements FluentRepositoryContextAware<User> {
        FluentRepositoryContext<User> context;

        @Override
        public FluentRepositoryContext<User> getFluentRepositoryContext() {
            return context;
        }

        @Override
        public void setFluentRepositoryContext(FluentRepositoryContext<User> context) {
            this.context = context;
        }

        @SuppressWarnings("unused")
        public void customMethod() {}
    }

    interface NonAwareFragment {
        void plainMethod();
    }
}
