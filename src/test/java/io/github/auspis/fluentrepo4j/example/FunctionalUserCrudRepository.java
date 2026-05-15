package io.github.auspis.fluentrepo4j.example;

import io.github.auspis.fluentrepo4j.functional.FunctionalCrudRepository;
import io.github.auspis.fluentrepo4j.test.domain.User;

public interface FunctionalUserCrudRepository extends FunctionalCrudRepository<User, Long> {}
