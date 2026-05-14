package io.github.auspis.fluentrepo4j.functional.write;

import java.util.List;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

@NoRepositoryBean
public interface FunctionalWriteRepository<T, ID> extends Repository<T, ID> {

    <S extends T> WriteResult<S> save(S entity);

    <S extends T> WriteResult<List<S>> saveAll(Iterable<S> entities);

    WriteResult<Boolean> deleteById(ID id);

    WriteResult<Boolean> delete(T entity);

    WriteResult<Long> deleteAllById(Iterable<? extends ID> ids);

    WriteResult<Long> deleteAll(Iterable<? extends T> entities);

    WriteResult<Long> deleteAll();
}
