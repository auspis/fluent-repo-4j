package io.github.auspis.fluentrepo4j.repository.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityRowMapper;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityWriter;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FluentRepositoryContextTest {

    @Test
    void constructionOk() {
        FluentRepositoryContext<User> context = buildContext();

        assertThat(context.dsl()).isNotNull();
        assertThat(context.connectionProvider()).isNotNull();
        assertThat(context.rowMapper()).isNotNull().isInstanceOf(FluentEntityRowMapper.class);
        assertThat(context.writer()).isNotNull().isInstanceOf(FluentEntityWriter.class);
    }

    @Test
    void dslDelegatesToInfrastructure() {
        DSL dsl = Mockito.mock(DSL.class);
        FluentConnectionProvider provider = Mockito.mock(FluentConnectionProvider.class);
        FluentRepositoryContext<User> context = FluentRepositoryContextFactory.create(dsl, provider, User.class);

        assertThat(context.dsl()).isSameAs(dsl);
    }

    @Test
    void connectionProviderDelegatesToInfrastructure() {
        DSL dsl = Mockito.mock(DSL.class);
        FluentConnectionProvider provider = Mockito.mock(FluentConnectionProvider.class);
        FluentRepositoryContext<User> context = FluentRepositoryContextFactory.create(dsl, provider, User.class);

        assertThat(context.connectionProvider()).isSameAs(provider);
    }

    @Test
    void constructionNullInfrastructure() {
        assertThatThrownBy(() -> new FluentRepositoryContext<User>(null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("FluentRepositoryInfrastructure must not be null");
    }

    @SuppressWarnings("unchecked")
    @Test
    void constructionNullRowMapper() {
        FluentRepositoryInfrastructure infra = new FluentRepositoryInfrastructure(
                Mockito.mock(DSL.class), Mockito.mock(FluentConnectionProvider.class));
        FluentEntityWriter<User> writer = Mockito.mock(FluentEntityWriter.class);

        assertThatThrownBy(() -> new FluentRepositoryContext<User>(infra, null, writer))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("FluentEntityRowMapper must not be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructionNullWriter() {
        FluentRepositoryInfrastructure infra = new FluentRepositoryInfrastructure(
                Mockito.mock(DSL.class), Mockito.mock(FluentConnectionProvider.class));
        FluentEntityRowMapper<User> rowMapper = Mockito.mock(FluentEntityRowMapper.class);

        assertThatThrownBy(() -> new FluentRepositoryContext<>(infra, rowMapper, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("FluentEntityWriter must not be null");
    }

    private FluentRepositoryContext<User> buildContext() {
        DSL dsl = Mockito.mock(DSL.class);
        FluentConnectionProvider provider = Mockito.mock(FluentConnectionProvider.class);
        return FluentRepositoryContextFactory.create(dsl, provider, User.class);
    }
}
