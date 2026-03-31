package io.github.auspis.fluentrepo4j.test.fragment.mixed;

import io.github.auspis.fluentrepo4j.test.domain.User;

import org.springframework.data.repository.CrudRepository;

/**
 * Repository with both an aware (DSL-powered) and a non-aware (plain) custom fragment.
 * Proves both fragment types coexist on a single repository.
 */
public interface MixedUserRepository extends CrudRepository<User, Long>, MixedAwareQueries, MixedPlainQueries {}
