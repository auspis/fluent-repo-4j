package io.github.auspis.fluentrepo4j.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Persistable;

class FluentEntityInformationTest {

    @Nested
    class Annotations {

        @Test
        void annotatedEntity_tableName() {
            FluentEntityInformation<AnnotatedUser, Long> info = new FluentEntityInformation<>(AnnotatedUser.class);
            assertThat(info.getTableName()).isEqualTo("users");
        }

        @Test
        void annotatedEntity_idColumn() {
            FluentEntityInformation<AnnotatedUser, Long> info = new FluentEntityInformation<>(AnnotatedUser.class);
            assertThat(info.getIdColumnName()).isEqualTo("user_id");
            assertThat(info.getIdType()).isEqualTo(Long.class);
        }

        @Test
        void annotatedEntity_columnMappings() {
            FluentEntityInformation<AnnotatedUser, Long> info = new FluentEntityInformation<>(AnnotatedUser.class);
            assertThat(info.getColumnToFieldMap()).containsKeys("user_id", "user_name", "email");
            assertThat(info.getColumnToFieldMap()).doesNotContainKey("session_token"); // @Transient excluded
        }

        @Test
        void conventionEntity_tableName() {
            FluentEntityInformation<ConventionEntity, Long> info =
                    new FluentEntityInformation<>(ConventionEntity.class);
            assertThat(info.getTableName()).isEqualTo("convention_entity");
        }

        @Test
        void conventionEntity_snakeCaseColumns() {
            FluentEntityInformation<ConventionEntity, Long> info =
                    new FluentEntityInformation<>(ConventionEntity.class);
            assertThat(info.getColumnToFieldMap()).containsKeys("id", "first_name", "last_name");
        }

        @Test
        void conventionEntity_idColumn() {
            FluentEntityInformation<ConventionEntity, Long> info =
                    new FluentEntityInformation<>(ConventionEntity.class);
            assertThat(info.getIdColumnName()).isEqualTo("id");
        }

        @Test
        void emptyTableName_fallsBackToSnakeCaseClassName() {
            FluentEntityInformation<EmptyNameTableEntity, Long> info =
                    new FluentEntityInformation<>(EmptyNameTableEntity.class);
            assertThat(info.getTableName()).isEqualTo("empty_name_table_entity");
        }

        @Test
        void emptyColumnName_fallsBackToSnakeCaseFieldName() {
            FluentEntityInformation<EmptyNameColumnsEntity, Long> info =
                    new FluentEntityInformation<>(EmptyNameColumnsEntity.class);
            assertThat(info.getIdColumnName()).isEqualTo("entity_id");
            assertThat(info.getColumnToFieldMap()).containsKey("entity_name");
        }

        @Test
        void staticAndTransientKeywordFields_areExcludedFromColumnMapping() {
            FluentEntityInformation<StaticAndTransientFieldsEntity, Long> info =
                    new FluentEntityInformation<>(StaticAndTransientFieldsEntity.class);
            assertThat(info.getColumnToFieldMap())
                    .containsKey("id")
                    .containsKey("normal_field")
                    .doesNotContainKey("static_field")
                    .doesNotContainKey("transient_keyword_field")
                    .doesNotContainKey("transient_annotated_field");
        }

        @Test
        void noIdEntity() {
            assertThatThrownBy(() -> new FluentEntityInformation<>(NoIdEntity.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No @Id field found");
        }

        @Test
        void springDataIdAnnotation() {
            FluentEntityInformation<SpringDataIdEntity, String> info =
                    new FluentEntityInformation<>(SpringDataIdEntity.class);
            assertThat(info.getIdColumnName()).isEqualTo("product_code");
            assertThat(info.getIdType()).isEqualTo(String.class);
            assertThat(info.getTableName()).isEqualTo("products");
        }

        @Test
        void nonIdColumnMap() {
            FluentEntityInformation<ConventionEntity, Long> info =
                    new FluentEntityInformation<>(ConventionEntity.class);
            Map<String, Field> nonId = info.getNonIdColumnToFieldMap();
            assertThat(nonId).containsKeys("first_name", "last_name").doesNotContainKey("id");
        }

        @Test
        void getId() {
            FluentEntityInformation<ConventionEntity, Long> info =
                    new FluentEntityInformation<>(ConventionEntity.class);
            ConventionEntity entity = new ConventionEntity();
            entity.id = 42L;
            assertThat(info.getId(entity)).isEqualTo(42L);
        }

        @Test
        void isNew() {
            FluentEntityInformation<ConventionEntity, Long> info =
                    new FluentEntityInformation<>(ConventionEntity.class);
            ConventionEntity entity = new ConventionEntity();
            assertThat(info.isNew(entity)).isTrue();

            entity.id = 1L;
            assertThat(info.isNew(entity)).isFalse();
        }
    }

    @Nested
    class GeneratedValueId {

        @Test
        void noGeneratedValue_strategyIsProvided() {
            FluentEntityInformation<ConventionEntity, Long> info =
                    new FluentEntityInformation<>(ConventionEntity.class);
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
    }

    @Nested
    class PersistableEntity {

        @Test
        void persistableEntity_delegatesToEntityIsNew() {
            FluentEntityInformation<PersistableEvent, UUID> info =
                    new FluentEntityInformation<>(PersistableEvent.class);
            PersistableEvent event = new PersistableEvent();

            assertThat(event.getId()).isNotNull();
            assertThat(info.isNew(event)).isTrue();

            event.markNotNew();
            assertThat(info.isNew(event)).isFalse();
        }

        @Test
        void nonPersistableEntity_usesIdNullCheck() {
            FluentEntityInformation<ConventionEntity, Long> info =
                    new FluentEntityInformation<>(ConventionEntity.class);
            ConventionEntity entity = new ConventionEntity();

            assertThat(info.isNew(entity)).isTrue();

            entity.id = 42L;
            assertThat(info.isNew(entity)).isFalse();
        }
    }

    @Nested
    class CustomId {

        @Test
        void setId_updatesEntityId() {
            FluentEntityInformation<ConventionEntity, Long> info =
                    new FluentEntityInformation<>(ConventionEntity.class);
            ConventionEntity entity = new ConventionEntity();
            assertThat(info.getId(entity)).isNull();

            info.setId(entity, 99L);

            assertThat(info.getId(entity)).isEqualTo(99L);
        }
    }

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

    @Table(name = "")
    static class EmptyNameTableEntity {
        @Id
        private Long id;
    }

    static class EmptyNameColumnsEntity {
        @Id
        @Column(name = "")
        private Long entityId;

        @Column(name = "")
        private String entityName;
    }

    static class StaticAndTransientFieldsEntity {
        @Id
        private Long id;

        @SuppressWarnings("unused")
        private String normalField;

        @SuppressWarnings("unused")
        private static String staticField;

        @SuppressWarnings("unused")
        private transient String transientKeywordField;

        @Transient
        @SuppressWarnings("unused")
        private String transientAnnotatedField;
    }

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
