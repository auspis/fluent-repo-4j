package io.github.auspis.fluentrepo4j.functional.read;

import java.util.List;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

@NoRepositoryBean
public interface FunctionalReadRepository<T, ID> extends Repository<T, ID> {

    ReadResult<T> findById(ID id);

    ReadResult<Boolean> existsById(ID id);

    ReadResult<List<T>> findAll();

    ReadResult<List<T>> findAllById(Iterable<ID> ids);

    ReadResult<Long> count();
}
