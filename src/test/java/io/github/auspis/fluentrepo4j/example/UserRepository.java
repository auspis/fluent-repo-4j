package io.github.auspis.fluentrepo4j.example;

import io.github.auspis.fluentrepo4j.test.domain.User;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Example repository for User entity.
 * Exposes both CRUD and paging/sorting capabilities in a single interface.
 */
public interface UserRepository extends CrudRepository<User, Long>, PagingAndSortingRepository<User, Long> {}
