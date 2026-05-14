package io.github.auspis.fluentrepo4j.repository;

import io.github.auspis.fluentrepo4j.functional.read.FunctionalReadPagingAndSortingRepository;
import io.github.auspis.fluentrepo4j.functional.read.FunctionalReadRepository;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult;
import io.github.auspis.fluentrepo4j.functional.write.FunctionalWriteRepository;
import io.github.auspis.fluentrepo4j.functional.write.WriteResult;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class FunctionalReadWriteFluentRepository<T, ID>
        implements FunctionalReadRepository<T, ID>,
                FunctionalReadPagingAndSortingRepository<T, ID>,
                FunctionalWriteRepository<T, ID> {

    private final FunctionalReadFluentRepository<T, ID> readRepository;
    private final FunctionalWriteFluentRepository<T, ID> writeRepository;

    public FunctionalReadWriteFluentRepository(CoreRepositoryOperations<T, ID> core) {
        this.readRepository = new FunctionalReadFluentRepository<>(core);
        this.writeRepository = new FunctionalWriteFluentRepository<>(core);
    }

    @Override
    public ReadResult<T> findById(ID id) {
        return readRepository.findById(id);
    }

    @Override
    public ReadResult<Boolean> existsById(ID id) {
        return readRepository.existsById(id);
    }

    @Override
    public ReadResult<List<T>> findAll() {
        return readRepository.findAll();
    }

    @Override
    public ReadResult<List<T>> findAllById(Iterable<ID> ids) {
        return readRepository.findAllById(ids);
    }

    @Override
    public ReadResult<Long> count() {
        return readRepository.count();
    }

    @Override
    public ReadResult<List<T>> findAll(Sort sort) {
        return readRepository.findAll(sort);
    }

    @Override
    public ReadResult<Page<T>> findAll(Pageable pageable) {
        return readRepository.findAll(pageable);
    }

    @Override
    public <S extends T> WriteResult<S> save(S entity) {
        return writeRepository.save(entity);
    }

    @Override
    public <S extends T> WriteResult<List<S>> saveAll(Iterable<S> entities) {
        return writeRepository.saveAll(entities);
    }

    @Override
    public WriteResult<Boolean> deleteById(ID id) {
        return writeRepository.deleteById(id);
    }

    @Override
    public WriteResult<Boolean> delete(T entity) {
        return writeRepository.delete(entity);
    }

    @Override
    public WriteResult<Long> deleteAllById(Iterable<? extends ID> ids) {
        return writeRepository.deleteAllById(ids);
    }

    @Override
    public WriteResult<Long> deleteAll(Iterable<? extends T> entities) {
        return writeRepository.deleteAll(entities);
    }

    @Override
    public WriteResult<Long> deleteAll() {
        return writeRepository.deleteAll();
    }
}
