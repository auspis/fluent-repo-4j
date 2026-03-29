package io.github.auspis.fluentrepo4j.repository.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentsql4j.dsl.DSL;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FluentRepositoryContextTest {

    @Test
    void constructionOk() {
        DSL dsl = Mockito.mock(DSL.class);
        FluentConnectionProvider provider = Mockito.mock(FluentConnectionProvider.class);

        FluentRepositoryContext context = new FluentRepositoryContext(dsl, provider);

        assertThat(context.dsl()).isSameAs(dsl);
        assertThat(context.connectionProvider()).isSameAs(provider);
    }

    @Test
    void constructionNullDsl() {
        FluentConnectionProvider provider = Mockito.mock(FluentConnectionProvider.class);

        assertThatThrownBy(() -> new FluentRepositoryContext(null, provider))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("DSL must not be null");
    }

    @Test
    void constructionNullConnectionProvider() {
        DSL dsl = Mockito.mock(DSL.class);

        assertThatThrownBy(() -> new FluentRepositoryContext(dsl, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("FluentConnectionProvider must not be null");
    }

    @Test
    void equalityOk() {
        DSL dsl = Mockito.mock(DSL.class);
        FluentConnectionProvider provider = Mockito.mock(FluentConnectionProvider.class);

        FluentRepositoryContext context1 = new FluentRepositoryContext(dsl, provider);
        FluentRepositoryContext context2 = new FluentRepositoryContext(dsl, provider);

        assertThat(context1).isEqualTo(context2).hasSameHashCodeAs(context2);
    }

    @Test
    void equalityKo() {
        DSL dsl1 = Mockito.mock(DSL.class);
        DSL dsl2 = Mockito.mock(DSL.class);
        FluentConnectionProvider provider = Mockito.mock(FluentConnectionProvider.class);

        FluentRepositoryContext context1 = new FluentRepositoryContext(dsl1, provider);
        FluentRepositoryContext context2 = new FluentRepositoryContext(dsl2, provider);

        assertThat(context1).isNotEqualTo(context2);
    }
}
