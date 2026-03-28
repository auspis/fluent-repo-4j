package io.github.auspis.fluentrepo4j.test.fragment.mixed;

import io.github.auspis.fluentrepo4j.test.domain.User;

import java.util.List;

/** Aware fragment interface for the mixed-fragment test. */
public interface MixedAwareQueries {

    List<User> findUsersByNameContaining(String namePart);
}
