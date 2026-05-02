package io.github.auspis.fluentrepo4j.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.auspis.fluentrepo4j.example.TestApplication;
import io.github.auspis.fluentrepo4j.example.UserRepository;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.hook.build.BuildHook;
import io.github.auspis.fluentsql4j.hook.build.BuildHookFactory;
import io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that a user-supplied {@link DSLRegistry} bean — created via
 * {@link DSLRegistry#create(BuildHookFactory)} — is used by repositories at query time,
 * and that the {@link BuildHookFactory} is invoked for each SQL statement built.
 *
 * <p>Demonstrates the Spring bean-override pattern for custom hook injection without any
 * changes to {@link FluentRepositoriesAutoConfiguration}: the user provides a {@code
 * DSLRegistry} bean backed by a custom factory; the auto-configuration's {@code
 * @ConditionalOnMissingBean} guard then skips its own default registry.
 */
@IntegrationTest
@SpringBootTest(classes = {TestApplication.class, BuildHookIntegrationTest.HookConfiguration.class})
@ActiveProfiles("test")
class BuildHookIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CountingBuildHookFactory hookFactory;

    @BeforeEach
    void resetHookCounter() {
        hookFactory.reset();
    }

    @Test
    void buildHookFactory_isInvokedWhenQueryIsExecuted() {
        userRepository.findAll();

        assertThat(hookFactory.getCallCount()).isPositive();
    }

    @Configuration(proxyBeanMethods = false)
    static class HookConfiguration {

        @Bean
        CountingBuildHookFactory countingBuildHookFactory() {
            return new CountingBuildHookFactory();
        }

        @Bean
        DSLRegistry fluentDslRegistry(CountingBuildHookFactory countingBuildHookFactory) {
            return DSLRegistry.create(countingBuildHookFactory);
        }
    }

    static final class CountingBuildHookFactory implements BuildHookFactory {

        private final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public BuildHook create() {
            callCount.incrementAndGet();
            return BuildHook.nullObject();
        }

        public int getCallCount() {
            return callCount.get();
        }

        public void reset() {
            callCount.set(0);
        }
    }
}
