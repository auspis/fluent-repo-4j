package io.github.auspis.fluentrepo4j.example;

import io.github.auspis.fluentrepo4j.functional.FunctionalCrudRepository;
import io.github.auspis.fluentrepo4j.functional.FunctionalPagingAndSortingRepository;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult;
import io.github.auspis.fluentrepo4j.test.domain.User;

import java.util.List;

/**
 * Functional repository for User entity.
 * Uses split read/write wrappers with explicit found/not-found/error semantics on reads.
 */
public interface FunctionalUserRepository
        extends FunctionalCrudRepository<User, Long>, FunctionalPagingAndSortingRepository<User, Long> {

    ReadResult<User> findByEmail(String email);

    ReadResult<List<User>> findByName(String name);

    ReadResult<Long> countByActive(Boolean active);

    ReadResult<Boolean> existsByEmail(String email);
}
