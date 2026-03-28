package io.github.auspis.fluentrepo4j.test.fragment.nonaware;

/**
 * Custom fragment interface for testing that fragments NOT implementing
 * {@link io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContextAware}
 * still work normally alongside the context-injection mechanism.
 */
public interface PlainQueries {

    String greetUser(String name);
}
