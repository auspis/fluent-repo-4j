package io.github.auspis.fluentrepo4j.example;

import io.github.auspis.fluentrepo4j.test.domain.User;

import org.springframework.data.repository.PagingAndSortingRepository;

public interface UserPagingRepository extends PagingAndSortingRepository<User, Long> {}
