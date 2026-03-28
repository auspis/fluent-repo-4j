package io.github.auspis.fluentrepo4j.test.fragment.postfix;

import io.github.auspis.fluentrepo4j.test.domain.User;

import java.util.List;

/**
 * Custom fragment interface for testing non-default repositoryImplementationPostfix.
 * The implementation class is named {@code PostfixQueriesCustom} (not Impl).
 */
public interface PostfixQueries {

    List<User> findUsersByEmail(String email);
}
