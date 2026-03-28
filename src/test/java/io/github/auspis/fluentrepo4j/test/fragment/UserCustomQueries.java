package io.github.auspis.fluentrepo4j.test.fragment;

import io.github.auspis.fluentrepo4j.test.domain.User;

import java.util.List;

/**
 * Custom query fragment interface for testing custom repository fragment support.
 */
public interface UserCustomQueries {

    List<User> findUsersByNameContaining(String namePart);

    long countActiveUsers();
}
