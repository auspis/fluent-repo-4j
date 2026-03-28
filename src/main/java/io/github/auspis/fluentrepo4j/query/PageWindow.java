package io.github.auspis.fluentrepo4j.query;

/**
 * A neutral pagination window expressed as page size and byte offset.
 *
 * @param pageSize number of rows per page
 * @param offset   zero-based row offset
 */
public record PageWindow(int pageSize, long offset) {}
