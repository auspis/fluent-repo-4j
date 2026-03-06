package io.github.auspis.fluentrepo4j.config;

import java.lang.annotation.Annotation;

import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;

/**
 * {@link org.springframework.context.annotation.ImportBeanDefinitionRegistrar} to enable
 * {@link EnableFluentRepositories} annotation.
 */
class FluentRepositoriesRegistrar extends RepositoryBeanDefinitionRegistrarSupport {

    @Override
    protected Class<? extends Annotation> getAnnotation() {
        return EnableFluentRepositories.class;
    }

    @Override
    protected RepositoryConfigurationExtension getExtension() {
        return new FluentRepositoryConfigExtension();
    }
}
