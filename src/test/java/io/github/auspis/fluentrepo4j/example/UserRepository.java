package io.github.auspis.fluentrepo4j.example;

import org.springframework.data.repository.CrudRepository;

/**
 * Example repository for User entity.
 * Extends CrudRepository to inherit CRUD operations:
 * - save(User)
 * - findById(Long)
 * - findAll()
 * - count()
 * - deleteById(Long)
 */
public interface UserRepository extends CrudRepository<User, Long> {
}

