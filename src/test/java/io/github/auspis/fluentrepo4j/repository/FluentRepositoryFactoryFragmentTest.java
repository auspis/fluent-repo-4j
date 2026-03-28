package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContext;
import io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContextAware;
import io.github.auspis.fluentsql4j.dsl.DSL;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.data.repository.core.support.RepositoryFragment;

/**
 * Unit test verifying that {@link FluentRepositoryFactory} injects
 * {@link FluentRepositoryContext} into {@link FluentRepositoryContextAware}
 * fragment implementations when building the repository proxy.
 */
class FluentRepositoryFactoryFragmentTest {

    private final DSL dsl = mock(DSL.class);
    private final FluentConnectionProvider connectionProvider = mock(FluentConnectionProvider.class);
    private final FluentRepositoryFactory factory = new FluentRepositoryFactory(connectionProvider, dsl);

    @Test
    void awareFragmentReceivesContext() {
        AwareFragment aware = new AwareFragment();
        RepositoryFragments fragments = RepositoryFragments.of(RepositoryFragment.implemented(aware));

        factory.injectFluentContext(fragments);

        assertThat(aware.context).isNotNull();
        assertThat(aware.context.dsl()).isSameAs(dsl);
        assertThat(aware.context.connectionProvider()).isSameAs(connectionProvider);
    }

    @Test
    void nonAwareFragmentNotInjected() {
        NonAwareFragment nonAware = mock(NonAwareFragment.class);
        RepositoryFragments fragments = RepositoryFragments.of(RepositoryFragment.implemented(nonAware));

        factory.injectFluentContext(fragments);

        verifyNoInteractions(nonAware);
    }

    @Test
    void mixedFragmentsOnlyAwareOnesInjected() {
        AwareFragment aware = new AwareFragment();
        NonAwareFragment nonAware = mock(NonAwareFragment.class);
        RepositoryFragments fragments =
                RepositoryFragments.of(RepositoryFragment.implemented(aware), RepositoryFragment.implemented(nonAware));

        factory.injectFluentContext(fragments);

        assertThat(aware.context).isNotNull();
        verifyNoInteractions(nonAware);
    }

    @Test
    void emptyFragmentsDoesNotFail() {
        RepositoryFragments fragments = RepositoryFragments.empty();

        factory.injectFluentContext(fragments);
    }

    @Test
    void injectedContextCarriesCorrectInstances() {
        SpyAwareFragment spy = mock(SpyAwareFragment.class);
        RepositoryFragments fragments = RepositoryFragments.of(RepositoryFragment.implemented(spy));

        factory.injectFluentContext(fragments);

        ArgumentCaptor<FluentRepositoryContext> captor = ArgumentCaptor.forClass(FluentRepositoryContext.class);
        verify(spy).setFluentRepositoryContext(captor.capture());
        FluentRepositoryContext injected = captor.getValue();
        assertThat(injected.dsl()).isSameAs(dsl);
        assertThat(injected.connectionProvider()).isSameAs(connectionProvider);
    }

    // -- Test helpers --

    static class AwareFragment implements FluentRepositoryContextAware {
        FluentRepositoryContext context;

        @Override
        public void setFluentRepositoryContext(FluentRepositoryContext context) {
            this.context = context;
        }

        @SuppressWarnings("unused")
        public void customMethod() {}
    }

    interface NonAwareFragment {
        void plainMethod();
    }

    interface SpyAwareFragment extends FluentRepositoryContextAware {
        void customMethod();
    }
}
