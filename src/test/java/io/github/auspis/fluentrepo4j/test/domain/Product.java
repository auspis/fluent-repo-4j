package io.github.auspis.fluentrepo4j.test.domain;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Test entity implementing {@link Persistable} for full control over the new/existing distinction.
 * Maps to the "products" table created by {@code TestDatabaseUtil.createProductsTable()}.
 */
@Table(name = "products")
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@AllArgsConstructor(access = lombok.AccessLevel.PUBLIC)
@Data
public class Product implements Persistable<Integer> {

    @Id
    private Integer id;

    private String name;
    private Double price;
    private Integer quantity;
    private String metadata;

    @Transient
    private boolean isNewEntity = true;

    public Product(Integer id, String name, Double price, Integer quantity) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.isNewEntity = true;
    }

    @Override
    public boolean isNew() {
        return isNewEntity;
    }

    /** Mark this entity as not new (already persisted). */
    public void markPersisted() {
        this.isNewEntity = false;
    }
}
