package io.github.auspis.fluentrepo4j.mapping;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Persistable;
import org.springframework.data.repository.core.support.AbstractEntityInformation;

/**
 * Provides entity metadata for Fluent SQL repositories by scanning JPA annotations
 * ({@code @Table}, {@code @Column}, {@code @Id}) and falling back to naming conventions.
 *
 * @param <T>  the entity type
 * @param <ID> the entity's identifier type
 */
public class FluentEntityInformation<T, ID> extends AbstractEntityInformation<T, ID> {

    private final String tableName;
    private final String idColumnName;
    private final Field idField;
    private final Class<ID> idType;
    private final IdGenerationStrategy idGenerationStrategy;
    private final Map<String, Field> columnToField; // column_name → Field
    private final Map<Field, String> fieldToColumn; // Field → column_name

    @SuppressWarnings("unchecked")
    public FluentEntityInformation(Class<T> domainClass) {
        super(domainClass);

        Field foundIdField = null;
        Class<ID> foundIdType = null;
        Map<String, Field> colToField = new LinkedHashMap<>();
        Map<Field, String> fieldToCol = new LinkedHashMap<>();

        for (Field field : domainClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            if (field.isAnnotationPresent(Transient.class)) {
                continue;
            }

            field.setAccessible(true);

            // Detect @Id (JPA or Spring Data)
            if (field.isAnnotationPresent(jakarta.persistence.Id.class)
                    || field.isAnnotationPresent(org.springframework.data.annotation.Id.class)) {
                foundIdField = field;
                foundIdType = (Class<ID>) field.getType();
            }

            // Resolve column name
            Column columnAnn = field.getAnnotation(Column.class);
            String columnName = (columnAnn != null && !columnAnn.name().isEmpty())
                    ? columnAnn.name()
                    : NamingUtils.toSnakeCase(field.getName());

            colToField.put(columnName, field);
            fieldToCol.put(field, columnName);
        }

        if (foundIdField == null) {
            throw new IllegalArgumentException("No @Id field found on entity " + domainClass.getName()
                    + ". Annotate the identifier field with @jakarta.persistence.Id"
                    + " or @org.springframework.data.annotation.Id.");
        }

        this.idField = foundIdField;
        this.idType = foundIdType;
        this.idGenerationStrategy = resolveIdGenerationStrategy(foundIdField);
        this.columnToField = Collections.unmodifiableMap(colToField);
        this.fieldToColumn = Collections.unmodifiableMap(fieldToCol);

        // Resolve ID column name
        Column idColumnAnn = idField.getAnnotation(Column.class);
        this.idColumnName = (idColumnAnn != null && !idColumnAnn.name().isEmpty())
                ? idColumnAnn.name()
                : NamingUtils.toSnakeCase(idField.getName());

        // Resolve table name
        Table tableAnn = domainClass.getAnnotation(Table.class);
        this.tableName = (tableAnn != null && !tableAnn.name().isEmpty())
                ? tableAnn.name()
                : NamingUtils.toSnakeCase(domainClass.getSimpleName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public ID getId(T entity) {
        try {
            return (ID) idField.get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read @Id field " + idField.getName(), e);
        }
    }

    @Override
    public Class<ID> getIdType() {
        return idType;
    }

    /**
     * Determines whether the given entity is new (not yet persisted).
     * <p>
     * If the entity implements {@link Persistable}, delegates to {@link Persistable#isNew()},
     * giving the developer full control over the new/existing distinction.
     * Otherwise, falls back to the standard Spring Data logic (ID == null → new).
     * </p>
     *
     * @param entity the entity to check
     * @return {@code true} if the entity should be treated as new (INSERT), {@code false} otherwise (UPDATE)
     */
    @Override
    public boolean isNew(T entity) {
        if (entity instanceof Persistable<?> persistable) {
            return persistable.isNew();
        }
        return super.isNew(entity);
    }

    public IdGenerationStrategy getIdGenerationStrategy() {
        return idGenerationStrategy;
    }

    public String getTableName() {
        return tableName;
    }

    public String getIdColumnName() {
        return idColumnName;
    }

    public Field getIdField() {
        return idField;
    }

    /**
     * Returns an unmodifiable map of column name → Field for ALL columns (including ID).
     */
    public Map<String, Field> getColumnToFieldMap() {
        return columnToField;
    }

    /**
     * Returns an unmodifiable map of Field → column name for ALL fields (including ID).
     */
    public Map<Field, String> getFieldToColumnMap() {
        return fieldToColumn;
    }

    /**
     * Returns column→Field entries excluding the ID column.
     */
    public Map<String, Field> getNonIdColumnToFieldMap() {
        Map<String, Field> result = new LinkedHashMap<>(columnToField);
        result.remove(idColumnName);
        return result;
    }

    /**
     * Sets the ID value on the given entity.
     *
     * @param entity the entity to update
     * @param id     the ID value to set
     */
    public void setId(T entity, ID id) {
        try {
            idField.set(entity, id);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot set @Id field " + idField.getName(), e);
        }
    }

    private static IdGenerationStrategy resolveIdGenerationStrategy(Field idField) {
        GeneratedValue generatedValue = idField.getAnnotation(GeneratedValue.class);
        if (generatedValue == null) {
            return IdGenerationStrategy.PROVIDED;
        }
        GenerationType strategy = generatedValue.strategy();
        if (strategy == GenerationType.IDENTITY) {
            return IdGenerationStrategy.IDENTITY;
        }
        throw new UnsupportedOperationException("ID generation strategy " + strategy + " is not supported. "
                + "Only GenerationType.IDENTITY is supported. "
                + "Remove @GeneratedValue for application-provided IDs.");
    }
}
