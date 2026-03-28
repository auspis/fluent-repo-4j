package io.github.auspis.fluentrepo4j.test.fragment;

import io.github.auspis.fluentrepo4j.test.domain.User;

import org.springframework.data.repository.CrudRepository;

/**
 * Test repository combining CRUD operations with custom fragment queries.
 */
public interface UserWithCustomQueriesRepository extends CrudRepository<User, Long>, UserCustomQueries {}
