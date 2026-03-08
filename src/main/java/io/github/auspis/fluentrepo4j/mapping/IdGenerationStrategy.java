package io.github.auspis.fluentrepo4j.mapping;

/**
 * Defines how the entity's primary key (ID) is generated.
 *
 * <ul>
 *   <li>{@link #PROVIDED} – The application is responsible for setting the ID before calling
 *       {@code save()}. This is the default when no {@code @GeneratedValue} annotation is present
 *       on the {@code @Id} field.</li>
 *   <li>{@link #IDENTITY} – The database generates the ID via an auto-increment / identity column.
 *       Corresponds to {@code @GeneratedValue(strategy = GenerationType.IDENTITY)}.</li>
 * </ul>
 */
public enum IdGenerationStrategy {

    /**
     * The application provides the ID value before persisting.
     * Used when no {@code @GeneratedValue} annotation is present.
     */
    PROVIDED,

    /**
     * The database auto-generates the ID (e.g., {@code IDENTITY}, {@code SERIAL},
     * {@code AUTO_INCREMENT}).
     * Corresponds to {@code @GeneratedValue(strategy = GenerationType.IDENTITY)}.
     */
    IDENTITY
}
