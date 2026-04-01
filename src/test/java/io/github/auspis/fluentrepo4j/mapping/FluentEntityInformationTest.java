package io.github.auspis.fluentrepo4j.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Persistable;

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
        assertThat(nonId).containsKeys("first_name", "last_name").doesNotContainKey("id");
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
        private String email; // no @Column → fallback to "email"

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

    // ---- @GeneratedValue tests ----

    @Test
    void noGeneratedValue_strategyIsProvided() {
        FluentEntityInformation<ConventionEntity, Long> info = new FluentEntityInformation<>(ConventionEntity.class);
        assertThat(info.getIdGenerationStrategy()).isEqualTo(IdGenerationStrategy.PROVIDED);
    }

    @Test
    void generatedValueIdentity_strategyIsIdentity() {
        FluentEntityInformation<IdentityEntity, Long> info = new FluentEntityInformation<>(IdentityEntity.class);
        assertThat(info.getIdGenerationStrategy()).isEqualTo(IdGenerationStrategy.IDENTITY);
    }

    @Test
    void generatedValueSequence_throwsUnsupported() {
        assertThatThrownBy(() -> new FluentEntityInformation<>(SequenceEntity.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("SEQUENCE")
                .hasMessageContaining("not supported");
    }

    @Test
    void generatedValueTable_throwsUnsupported() {
        assertThatThrownBy(() -> new FluentEntityInformation<>(TableStrategyEntity.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("TABLE")
                .hasMessageContaining("not supported");
    }

    @Test
    void generatedValueAuto_throwsUnsupported() {
        assertThatThrownBy(() -> new FluentEntityInformation<>(AutoStrategyEntity.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("AUTO")
                .hasMessageContaining("not supported");
    }

    // ---- Persistable isNew() tests ----

    @Test
    void persistableEntity_delegatesToEntityIsNew() {
        FluentEntityInformation<PersistableEvent, UUID> info = new FluentEntityInformation<>(PersistableEvent.class);
        PersistableEvent event = new PersistableEvent();

        // ID is non-null (set in constructor), but isNew() returns true
        assertThat(event.getId()).isNotNull();
        assertThat(info.isNew(event)).isTrue();

        // After marking as not new, isNew() returns false
        event.markNotNew();
        assertThat(info.isNew(event)).isFalse();
    }

    @Test
    void nonPersistableEntity_usesIdNullCheck() {
        FluentEntityInformation<ConventionEntity, Long> info = new FluentEntityInformation<>(ConventionEntity.class);
        ConventionEntity entity = new ConventionEntity();

        // ID is null → isNew returns true
        assertThat(info.isNew(entity)).isTrue();

        // ID is set → isNew returns false
        entity.id = 42L;
        assertThat(info.isNew(entity)).isFalse();
    }

    // ---- setId tests ----

    @Test
    void setId_updatesEntityId() {
        FluentEntityInformation<ConventionEntity, Long> info = new FluentEntityInformation<>(ConventionEntity.class);
        ConventionEntity entity = new ConventionEntity();
        assertThat(info.getId(entity)).isNull();

        info.setId(entity, 99L);

        assertThat(info.getId(entity)).isEqualTo(99L);
    }

    // ---- Test entities for @GeneratedValue ----

    static class IdentityEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @SuppressWarnings("unused")
        private String name;
    }

    static class SequenceEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;
    }

    static class TableStrategyEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.TABLE)
        private Long id;
    }

    static class AutoStrategyEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private Long id;
    }

    // ---- Test entity for Persistable ----

    static class PersistableEvent implements Persistable<UUID> {
        @Id
        private UUID id;

        @Transient
        private boolean isNew = true;

        PersistableEvent() {
            this.id = UUID.randomUUID();
        }

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public boolean isNew() {
            return isNew;
        }

        void markNotNew() {
            this.isNew = false;
        }
    }
}
