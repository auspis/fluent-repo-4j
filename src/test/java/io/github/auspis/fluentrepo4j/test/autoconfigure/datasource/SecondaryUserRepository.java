package io.github.auspis.fluentrepo4j.test.autoconfigure.datasource;

import io.github.auspis.fluentrepo4j.test.domain.User;

import org.springframework.data.repository.CrudRepository;

public interface SecondaryUserRepository extends CrudRepository<User, Long> {}
