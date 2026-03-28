package io.github.auspis.fluentrepo4j.test.fragment.nonaware;

import io.github.auspis.fluentrepo4j.test.domain.User;

import org.springframework.data.repository.CrudRepository;

/**
 * Test repository with a non-aware custom fragment.
 * Verifies that repositories with fragments that don't implement
 * {@link io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContextAware}
 * still bootstrap and work correctly.
 */
public interface PlainUserRepository extends CrudRepository<User, Long>, PlainQueries {}
