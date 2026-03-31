package io.github.auspis.fluentrepo4j.test.fragment.nonaware;

/**
 * Non-aware fragment implementation. Does NOT implement
 * {@link io.github.auspis.fluentrepo4j.repository.context.FluentRepositoryContextAware}.
 * Proves that the injection mechanism is opt-in and does not break regular fragments.
 */
public class PlainQueriesImpl implements PlainQueries {

    @Override
    public String greetUser(String name) {
        return "Hello, " + name;
    }
}
