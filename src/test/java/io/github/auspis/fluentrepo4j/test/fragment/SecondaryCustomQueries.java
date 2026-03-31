package io.github.auspis.fluentrepo4j.test.fragment;

import io.github.auspis.fluentrepo4j.test.domain.User;

import java.util.List;

/**
 * Custom query fragment for the secondary datasource repository.
 */
public interface SecondaryCustomQueries {

    List<User> findUsersByNamePrefix(String prefix);
}
