package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.data.repository.Repository;

class FluentRepositoryFactoryBeanTest {

    interface DummyRepository extends Repository<DummyEntity, Long> {}

    static class DummyEntity {
        @jakarta.persistence.Id
        private Long id;
    }

    @Test
    void resolveUniqueBean_withoutBeanFactory_throwsIllegalState() {
        FluentRepositoryFactoryBean<DummyRepository, DummyEntity, Long> factoryBean =
                new FluentRepositoryFactoryBean<>(DummyRepository.class);

        assertThatThrownBy(() -> invokeResolveUniqueBean(factoryBean, DataSource.class, "hint", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BeanFactory was not initialized");
    }

    @Test
    void resolveUniqueBean_optionalMissing_returnsNull() throws Exception {
        FluentRepositoryFactoryBean<DummyRepository, DummyEntity, Long> factoryBean =
                new FluentRepositoryFactoryBean<>(DummyRepository.class);
        ListableBeanFactory beanFactory = mock(ListableBeanFactory.class);
        factoryBean.setBeanFactory(beanFactory);

        when(beanFactory.getBean(DataSource.class)).thenThrow(new NoSuchBeanDefinitionException(DataSource.class));

        Object result = invokeResolveUniqueBean(factoryBean, DataSource.class, "hint", false);

        assertThat(result).isNull();
    }

    @Test
    void resolveUniqueBean_requiredMissing_throwsIllegalState() throws Exception {
        FluentRepositoryFactoryBean<DummyRepository, DummyEntity, Long> factoryBean =
                new FluentRepositoryFactoryBean<>(DummyRepository.class);
        ListableBeanFactory beanFactory = mock(ListableBeanFactory.class);
        factoryBean.setBeanFactory(beanFactory);

        when(beanFactory.getBean(DataSource.class)).thenThrow(new NoSuchBeanDefinitionException(DataSource.class));

        assertThatThrownBy(() -> invokeResolveUniqueBean(factoryBean, DataSource.class, "custom hint", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No bean of type DataSource")
                .hasMessageContaining("custom hint");
    }

    @Test
    void resolveUniqueBean_ambiguous_throwsIllegalStateWithSortedBeanNames() throws Exception {
        FluentRepositoryFactoryBean<DummyRepository, DummyEntity, Long> factoryBean =
                new FluentRepositoryFactoryBean<>(DummyRepository.class);
        ListableBeanFactory beanFactory = mock(ListableBeanFactory.class);
        factoryBean.setBeanFactory(beanFactory);

        NoUniqueBeanDefinitionException ambiguous =
                new NoUniqueBeanDefinitionException(DataSource.class, List.of("zDataSource", "aDataSource"));
        when(beanFactory.getBean(DataSource.class)).thenThrow(ambiguous);

        assertThatThrownBy(() -> invokeResolveUniqueBean(factoryBean, DataSource.class, "use @Primary", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple beans of type DataSource")
                .hasMessageContaining("aDataSource, zDataSource")
                .hasMessageContaining("use @Primary");
    }

    private static Object invokeResolveUniqueBean(
            FluentRepositoryFactoryBean<?, ?, ?> target, Class<?> beanType, String hint, boolean required)
            throws Exception {
        Method method = FluentRepositoryFactoryBean.class.getDeclaredMethod(
                "resolveUniqueBean", Class.class, String.class, boolean.class);
        method.setAccessible(true);
        try {
            return method.invoke(target, beanType, hint, required);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
