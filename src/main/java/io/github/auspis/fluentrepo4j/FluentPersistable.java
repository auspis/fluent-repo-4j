package io.github.auspis.fluentrepo4j;

import org.springframework.data.domain.Persistable;

/**
 * Extension of Spring Data's {@link Persistable} that participates in the
 * fluent-repo-4j persistence lifecycle.
 *
 * <p>Implementing this interface allows the entity to control the INSERT vs UPDATE
 * decision via {@link #isNew()}, while delegating state management to the library:
 * <ul>
 *   <li>{@link #markPersisted()} is called automatically by {@code FluentRepository}
 *       after a successful {@code save()} (INSERT or UPDATE).</li>
 *   <li>{@link #markPersisted()} is called automatically by {@code FluentEntityRowMapper}
 *       after loading an entity from the database.</li>
 * </ul>
 *
 * <p><strong>Note</strong>: {@code @PostLoad} and {@code @PostPersist} are JPA lifecycle
 * callbacks and are <em>not</em> triggered in pure JDBC mode. This interface is the
 * fluent-repo-4j equivalent.
 *
 * <h3>Typical implementation</h3>
 * <pre>{@code
 * @Table(name = "products")
 * public class Product implements FluentPersistable<Integer> {
 *
 *     @Id
 *     private Integer id;
 *
 *     @Transient
 *     private boolean isNewEntity = true;
 *
 *     @Override public Integer getId()      { return id; }
 *     @Override public boolean isNew()      { return isNewEntity; }
 *     @Override public void markPersisted() { this.isNewEntity = false; }
 * }
 * }</pre>
 *
 * @param <ID> the type of the entity's identifier
 */
public interface FluentPersistable<ID> extends Persistable<ID> {

    /**
     * Marks this entity as no longer new (i.e., it is now persisted in the database).
     * <p>
     * Called automatically by the library after {@code save()} and after loading
     * from the database. You do not need to call this manually.
     */
    void markPersisted();
}
