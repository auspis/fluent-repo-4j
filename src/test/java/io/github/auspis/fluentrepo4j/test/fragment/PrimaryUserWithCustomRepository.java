package io.github.auspis.fluentrepo4j.test.fragment;

import io.github.auspis.fluentrepo4j.test.domain.User;

import org.springframework.data.repository.CrudRepository;

/**
 * Repository for the primary datasource with custom fragment queries.
 */
public interface PrimaryUserWithCustomRepository extends CrudRepository<User, Long>, PrimaryCustomQueries {}
