package io.github.auspis.fluentrepo4j.test.fragment.mixed;

/** Non-aware fragment impl — no Fluent context, plain logic only. */
public class MixedPlainQueriesImpl implements MixedPlainQueries {

    @Override
    public String greetUser(String name) {
        return "Hello, " + name;
    }
}
