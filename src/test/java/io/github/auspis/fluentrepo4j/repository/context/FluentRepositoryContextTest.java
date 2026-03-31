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

    private DSL dsl = Mockito.mock(DSL.class);
    private FluentConnectionProvider provider = Mockito.mock(FluentConnectionProvider.class);

    @SuppressWarnings("unchecked")
    private FluentEntityWriter<User> writer = Mockito.mock(FluentEntityWriter.class);

    @SuppressWarnings("unchecked")
    private FluentEntityRowMapper<User> rowMapper = Mockito.mock(FluentEntityRowMapper.class);

    @Test
    void constructionOk() {
        FluentRepositoryContext<User> context = FluentRepositoryContextFactory.create(dsl, provider, User.class);
        assertThat(context.dsl()).isNotNull();
        assertThat(context.connectionProvider()).isNotNull();
        assertThat(context.rowMapper()).isNotNull().isInstanceOf(FluentEntityRowMapper.class);
        assertThat(context.writer()).isNotNull().isInstanceOf(FluentEntityWriter.class);
        assertThat(context.dsl()).isSameAs(dsl);
        assertThat(context.connectionProvider()).isSameAs(provider);
    }

    @Test
    void constructionNullInfrastructure() {
        assertThatThrownBy(() -> new FluentRepositoryContext<User>(null, rowMapper, writer))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("FluentRepositoryInfrastructure must not be null");
    }

    @Test
    void constructionNullRowMapper() {
        FluentRepositoryInfrastructure infra = new FluentRepositoryInfrastructure(dsl, provider);
        assertThatThrownBy(() -> new FluentRepositoryContext<User>(infra, null, writer))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("FluentEntityRowMapper must not be null");
    }

    @Test
    void constructionNullWriter() {
        FluentRepositoryInfrastructure infra = new FluentRepositoryInfrastructure(dsl, provider);
        assertThatThrownBy(() -> new FluentRepositoryContext<>(infra, rowMapper, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("FluentEntityWriter must not be null");
    }
}
