package io.github.auspis.fluentrepo4j.config;

import java.util.Collection;
import java.util.Collections;

import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport;

import io.github.auspis.fluentrepo4j.repository.FluentRepositoryFactoryBean;

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
    protected String getModulePrefix() {
        return "fluent";
    }

    @Override
    protected Collection<Class<?>> getIdentifyingTypes() {
        return Collections.singleton(org.springframework.data.repository.Repository.class);
    }
}
