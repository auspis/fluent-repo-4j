package io.github.auspis.fluentrepo4j.example;

import io.github.auspis.fluentrepo4j.functional.FunctionalCrudRepository;
import io.github.auspis.fluentrepo4j.functional.FunctionalPagingAndSortingRepository;
import io.github.auspis.fluentrepo4j.functional.RepositoryResult;
import io.github.auspis.fluentrepo4j.test.domain.User;

import java.util.List;
import java.util.Optional;

/**
 * Functional repository for User entity.
 * Uses {@link RepositoryResult} wrappers instead of exceptions for domain-level outcomes.
 */
public interface FunctionalUserRepository
        extends FunctionalCrudRepository<User, Long>, FunctionalPagingAndSortingRepository<User, Long> {

    RepositoryResult<Optional<User>> findByEmail(String email);

    RepositoryResult<List<User>> findByName(String name);

    RepositoryResult<Long> countByActive(Boolean active);

    RepositoryResult<Boolean> existsByEmail(String email);
}
