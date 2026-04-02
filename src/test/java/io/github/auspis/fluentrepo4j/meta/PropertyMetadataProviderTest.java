package io.github.auspis.fluentrepo4j.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

class PropertyMetadataProviderTest {

    @Test
    void resolveColumn_directPropertyName() {
        FluentEntityInformation<SimpleEntity, Long> info = new FluentEntityInformation<>(SimpleEntity.class);
        PropertyMetadataProvider<SimpleEntity, Long> provider = new PropertyMetadataProvider<>(info);

        assertThat(provider.resolveColumn("firstName")).isEqualTo("first_name");
    }

    @Test
    void resolveColumn_fallbackToSnakeCaseWhenPropertyLooksLikeColumn() {
        FluentEntityInformation<SimpleEntity, Long> info = new FluentEntityInformation<>(SimpleEntity.class);
        PropertyMetadataProvider<SimpleEntity, Long> provider = new PropertyMetadataProvider<>(info);

        // Not a Java field name, but valid column key present in columnToField map.
        assertThat(provider.resolveColumn("first_name")).isEqualTo("first_name");
    }

    @Test
    void resolveColumn_nestedPath_throwsIllegalArgument() {
        FluentEntityInformation<SimpleEntity, Long> info = new FluentEntityInformation<>(SimpleEntity.class);
        PropertyMetadataProvider<SimpleEntity, Long> provider = new PropertyMetadataProvider<>(info);

        assertThatThrownBy(() -> provider.resolveColumn("address.city"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nested property path")
                .hasMessageContaining("not supported");
    }

    @Test
    void resolveColumn_unknownProperty_throwsIllegalArgument() {
        FluentEntityInformation<SimpleEntity, Long> info = new FluentEntityInformation<>(SimpleEntity.class);
        PropertyMetadataProvider<SimpleEntity, Long> provider = new PropertyMetadataProvider<>(info);

        assertThatThrownBy(() -> provider.resolveColumn("unknownProperty"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Property 'unknownProperty' not found")
                .hasMessageContaining("SimpleEntity");
    }

    @Test
    void getTableName_returnsEntityTableName() {
        FluentEntityInformation<SimpleEntity, Long> info = new FluentEntityInformation<>(SimpleEntity.class);
        PropertyMetadataProvider<SimpleEntity, Long> provider = new PropertyMetadataProvider<>(info);

        assertThat(provider.getTableName()).isEqualTo("simple_entity");
    }

    @Table(name = "simple_entity")
    static class SimpleEntity {
        @Id
        private Long id;

        @SuppressWarnings("unused")
        private String firstName;
    }
}
