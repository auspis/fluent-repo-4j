package io.github.auspis.fluentrepo4j.repository;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentsql4j.dsl.DSL;

import org.springframework.beans.factory.annotation.Autowired;
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

    private FluentConnectionProvider connectionProvider;
    private DSL dsl;

    protected FluentRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
        super(repositoryInterface);
    }

    @Override
    protected RepositoryFactorySupport doCreateRepositoryFactory() {
        return new FluentRepositoryFactory(connectionProvider, dsl);
    }

    @Autowired
    public void setConnectionProvider(FluentConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Autowired
    public void setDsl(DSL dsl) {
        this.dsl = dsl;
    }
}
