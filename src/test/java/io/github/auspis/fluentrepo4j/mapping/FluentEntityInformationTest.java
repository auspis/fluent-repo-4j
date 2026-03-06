package io.github.auspis.fluentrepo4j.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

class FluentEntityInformationTest {

    // ---- Tests ----

    @Test
    void annotatedEntity_tableName() {
        var info = new FluentEntityInformation<>(AnnotatedUser.class);
        assertThat(info.getTableName()).isEqualTo("users");
    }

    @Test
    void annotatedEntity_idColumn() {
        var info = new FluentEntityInformation<>(AnnotatedUser.class);
        assertThat(info.getIdColumnName()).isEqualTo("user_id");
        assertThat(info.getIdType()).isEqualTo(Long.class);
    }

    @Test
    void annotatedEntity_columnMappings() {
        var info = new FluentEntityInformation<>(AnnotatedUser.class);
        assertThat(info.getColumnToFieldMap()).containsKeys("user_id", "user_name", "email");
        assertThat(info.getColumnToFieldMap()).doesNotContainKey("session_token"); // @Transient excluded
    }

    @Test
    void conventionEntity_tableName() {
        var info = new FluentEntityInformation<>(ConventionEntity.class);
        assertThat(info.getTableName()).isEqualTo("convention_entity");
    }

    @Test
    void conventionEntity_snakeCaseColumns() {
        var info = new FluentEntityInformation<>(ConventionEntity.class);
        assertThat(info.getColumnToFieldMap()).containsKeys("id", "first_name", "last_name");
    }

    @Test
    void conventionEntity_idColumn() {
        var info = new FluentEntityInformation<>(ConventionEntity.class);
        assertThat(info.getIdColumnName()).isEqualTo("id");
    }

    @Test
    void noIdEntity_throwsException() {
        assertThatThrownBy(() -> new FluentEntityInformation<>(NoIdEntity.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No @Id field found");
    }

    @Test
    void springDataIdAnnotation_works() {
        var info = new FluentEntityInformation<>(SpringDataIdEntity.class);
        assertThat(info.getIdColumnName()).isEqualTo("product_code");
        assertThat(info.getIdType()).isEqualTo(String.class);
        assertThat(info.getTableName()).isEqualTo("products");
    }

    @Test
    void nonIdColumnMap_excludesId() {
        var info = new FluentEntityInformation<>(ConventionEntity.class);
        var nonId = info.getNonIdColumnToFieldMap();
        assertThat(nonId).containsKeys("first_name", "last_name");
        assertThat(nonId).doesNotContainKey("id");
    }

    @Test
    void getId_returnsIdValue() {
        var info = new FluentEntityInformation<>(ConventionEntity.class);
        ConventionEntity entity = new ConventionEntity();
        entity.id = 42L;
        assertThat(info.getId(entity)).isEqualTo(42L);
    }

    @Test
    void isNew_detectedByNullId() {
        var info = new FluentEntityInformation<>(ConventionEntity.class);
        ConventionEntity entity = new ConventionEntity();
        assertThat(info.isNew(entity)).isTrue();

        entity.id = 1L;
        assertThat(info.isNew(entity)).isFalse();
    }

        // ---- Test entities ----

    @Table(name = "users")
    static class AnnotatedUser {
        @Id
        @Column(name = "user_id")
        private Long id;

        @Column(name = "user_name")
        private String name;

        @SuppressWarnings("unused")
        private String email;   // no @Column → fallback to "email"
        
        @Transient
        private String sessionToken;
    }
    
    static class ConventionEntity {
        @Id
        private Long id;
        
        @SuppressWarnings("unused")
        private String firstName;
        @SuppressWarnings("unused")
        private String lastName;
    }
    
    static class NoIdEntity {
        @SuppressWarnings("unused")
        private String name;
    }
    
    @Table(name = "products")
    static class SpringDataIdEntity {
        @org.springframework.data.annotation.Id
        private String productCode;
        
        @SuppressWarnings("unused")
        private String description;
    }
}
