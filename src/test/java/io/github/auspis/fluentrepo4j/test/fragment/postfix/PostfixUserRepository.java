package io.github.auspis.fluentrepo4j.test.fragment.postfix;

import io.github.auspis.fluentrepo4j.test.domain.User;

import org.springframework.data.repository.CrudRepository;

/**
 * Test repository with a custom fragment whose implementation uses a non-default postfix ("Custom").
 */
public interface PostfixUserRepository extends CrudRepository<User, Long>, PostfixQueries {}
