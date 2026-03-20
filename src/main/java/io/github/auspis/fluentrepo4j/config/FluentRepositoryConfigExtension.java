package io.github.auspis.fluentrepo4j.config;

import io.github.auspis.fluentrepo4j.repository.FluentRepositoryFactoryBean;

import java.util.Collection;
import java.util.Collections;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource;
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport;

/**
 * {@link org.springframework.data.repository.config.RepositoryConfigurationExtension}
 * for Fluent SQL repositories.
 */
public class FluentRepositoryConfigExtension extends RepositoryConfigurationExtensionSupport {

    @Override
    public String getModuleName() {
        return "Fluent";
    }

    @Override
    public String getRepositoryFactoryBeanClassName() {
        return FluentRepositoryFactoryBean.class.getName();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected String getModulePrefix() {
        return "fluent-repo-4j";
    }

    @Override
    protected Collection<Class<?>> getIdentifyingTypes() {
        return Collections.singleton(org.springframework.data.repository.Repository.class);
    }

    @Override
    public void postProcess(
            BeanDefinitionBuilder builder, AnnotationRepositoryConfigurationSource configurationSource) {
        addOptionalPropertyReference(builder, configurationSource, "dataSourceRef", "dataSource");
        addOptionalPropertyReference(builder, configurationSource, "dslRegistryRef", "dslRegistry");
        addOptionalPropertyReference(builder, configurationSource, "connectionProviderRef", "connectionProvider");
        addOptionalPropertyReference(builder, configurationSource, "dslRef", "dsl");
    }

    private static void addOptionalPropertyReference(
            BeanDefinitionBuilder builder,
            AnnotationRepositoryConfigurationSource configurationSource,
            String attributeName,
            String propertyName) {
        configurationSource
                .getAttribute(attributeName, String.class)
                .filter(beanName -> !beanName.isBlank())
                .ifPresent(beanName -> builder.addPropertyReference(propertyName, beanName));
    }
}
