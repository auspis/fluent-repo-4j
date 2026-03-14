package io.github.auspis.fluentrepo4j.repository;

/**
 * Represents the action to perform when saving an entity.
 * Combines the persistence operation (INSERT vs UPDATE) with the ID generation mechanism,
 * so that the caller can dispatch with a single exhaustive switch.
 */
public enum SaveAction {

    /** Insert using an application-provided ID (no {@code @GeneratedValue}). */
    INSERT_PROVIDED_ID,

    /** Insert letting the database generate the ID ({@code @GeneratedValue(strategy = IDENTITY)}). */
    INSERT_AUTO_ID,

    /** Update an existing row. */
    UPDATE,

    /** The entity state is inconsistent (e.g. IDENTITY strategy with a non-null ID that doesn't exist in DB). */
    ERROR
}
