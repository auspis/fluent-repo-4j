package io.github.auspis.fluentrepo4j.repository;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.dialect.DialectDetector;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;

import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.TransactionalRepositoryFactoryBeanSupport;

/**
 * {@link org.springframework.beans.factory.FactoryBean} that creates Fluent SQL repository proxies.
 * Extends {@link TransactionalRepositoryFactoryBeanSupport} to integrate with Spring's
 * transaction management.
 *
 * @param <T>  the repository type
 * @param <S>  the entity type
 * @param <ID> the entity identifier type
 */
public class FluentRepositoryFactoryBean<T extends Repository<S, ID>, S, ID>
        extends TransactionalRepositoryFactoryBeanSupport<T, S, ID> {

    private BeanFactory beanFactory;
    private DataSource dataSource;
    private DSLRegistry dslRegistry;
    private FluentConnectionProvider connectionProvider;
    private DSL dsl;

    protected FluentRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
        super(repositoryInterface);
    }

    @Override
    protected RepositoryFactorySupport doCreateRepositoryFactory() {
        FluentConnectionProvider resolvedConnectionProvider = resolveConnectionProvider();
        DSL resolvedDsl = resolveDsl();
        return new FluentRepositoryFactory(resolvedConnectionProvider, resolvedDsl);
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        super.setBeanFactory(beanFactory);
        this.beanFactory = beanFactory;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setDslRegistry(DSLRegistry dslRegistry) {
        this.dslRegistry = dslRegistry;
    }

    public void setConnectionProvider(FluentConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void setDsl(DSL dsl) {
        this.dsl = dsl;
    }

    private FluentConnectionProvider resolveConnectionProvider() {
        if (connectionProvider != null) {
            return connectionProvider;
        }

        return new FluentConnectionProvider(resolveDataSource());
    }

    private DSL resolveDsl() {
        if (dsl != null) {
            return dsl;
        }

        return DialectDetector.detect(resolveDataSource(), resolveDslRegistry());
    }

    private DataSource resolveDataSource() {
        if (dataSource != null) {
            return dataSource;
        }

        FluentConnectionProvider uniqueConnectionProvider = resolveUniqueBean(
                FluentConnectionProvider.class,
                "Specify connectionProviderRef or dataSourceRef on @EnableFluentRepositories, or define a single FluentConnectionProvider/DataSource bean.",
                false);

        if (uniqueConnectionProvider != null) {
            return uniqueConnectionProvider.getDataSource();
        }

        return resolveUniqueBean(
                DataSource.class,
                "Specify dataSourceRef on @EnableFluentRepositories, mark one DataSource as @Primary, or define a single DataSource bean.",
                true);
    }

    private DSLRegistry resolveDslRegistry() {
        if (dslRegistry != null) {
            return dslRegistry;
        }

        return resolveUniqueBean(
                DSLRegistry.class,
                "Specify dslRegistryRef on @EnableFluentRepositories or define a single DSLRegistry bean.",
                true);
    }

    private <B> B resolveUniqueBean(Class<B> beanType, String resolutionHint, boolean required) {
        if (beanFactory == null) {
            throw new IllegalStateException("BeanFactory was not initialized before repository factory creation.");
        }

        try {
            return beanFactory.getBean(beanType);
        } catch (NoUniqueBeanDefinitionException e) {
            throw new IllegalStateException(buildAmbiguousBeanMessage(beanType, e, resolutionHint), e);
        } catch (NoSuchBeanDefinitionException e) {
            if (!required) {
                return null;
            }
            throw new IllegalStateException(buildMissingBeanMessage(beanType, resolutionHint), e);
        }
    }

    private static String buildMissingBeanMessage(Class<?> beanType, String resolutionHint) {
        return "No bean of type " + beanType.getSimpleName() + " could be resolved for Fluent repositories. "
                + resolutionHint;
    }

    private static String buildAmbiguousBeanMessage(
            Class<?> beanType, NoUniqueBeanDefinitionException exception, String resolutionHint) {
        java.util.Collection<String> beanNamesFound = exception.getBeanNamesFound();
        String beanNames =
                beanNamesFound == null ? "" : beanNamesFound.stream().sorted().collect(Collectors.joining(", "));
        return "Multiple beans of type " + beanType.getSimpleName()
                + " were found for Fluent repositories"
                + (beanNames.isBlank() ? ". " : " [" + beanNames + "]. ")
                + resolutionHint;
    }
}
