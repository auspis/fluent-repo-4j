package io.github.auspis.fluentrepo4j.repository.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentsql4j.dsl.DSL;

import org.junit.jupiter.api.Test;

class FluentRepositoryContextTest {

    @Test
    void constructionWithValidArguments() {
        DSL dsl = mock(DSL.class);
        FluentConnectionProvider provider = mock(FluentConnectionProvider.class);

        FluentRepositoryContext context = new FluentRepositoryContext(dsl, provider);

        assertThat(context.dsl()).isSameAs(dsl);
        assertThat(context.connectionProvider()).isSameAs(provider);
    }

    @Test
    void nullDslThrowsNullPointerException() {
        FluentConnectionProvider provider = mock(FluentConnectionProvider.class);

        assertThatThrownBy(() -> new FluentRepositoryContext(null, provider))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("DSL must not be null");
    }

    @Test
    void nullConnectionProviderThrowsNullPointerException() {
        DSL dsl = mock(DSL.class);

        assertThatThrownBy(() -> new FluentRepositoryContext(dsl, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("FluentConnectionProvider must not be null");
    }

    @Test
    void equalityForSameComponents() {
        DSL dsl = mock(DSL.class);
        FluentConnectionProvider provider = mock(FluentConnectionProvider.class);

        FluentRepositoryContext context1 = new FluentRepositoryContext(dsl, provider);
        FluentRepositoryContext context2 = new FluentRepositoryContext(dsl, provider);

        assertThat(context1).isEqualTo(context2);
        assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
    }

    @Test
    void inequalityForDifferentComponents() {
        DSL dsl1 = mock(DSL.class);
        DSL dsl2 = mock(DSL.class);
        FluentConnectionProvider provider = mock(FluentConnectionProvider.class);

        FluentRepositoryContext context1 = new FluentRepositoryContext(dsl1, provider);
        FluentRepositoryContext context2 = new FluentRepositoryContext(dsl2, provider);

        assertThat(context1).isNotEqualTo(context2);
    }
}
