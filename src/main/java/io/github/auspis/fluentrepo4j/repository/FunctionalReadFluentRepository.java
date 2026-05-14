package io.github.auspis.fluentrepo4j.repository;

import io.github.auspis.fluentrepo4j.functional.read.FunctionalReadPagingAndSortingRepository;
import io.github.auspis.fluentrepo4j.functional.read.FunctionalReadRepository;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Error;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Found;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.NotFound;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class FunctionalReadFluentRepository<T, ID>
        implements FunctionalReadRepository<T, ID>, FunctionalReadPagingAndSortingRepository<T, ID> {

    @FunctionalInterface
    private interface ReadSupplier<R> {
        R get();
    }

    private final CoreRepositoryOperations<T, ID> core;

    public FunctionalReadFluentRepository(CoreRepositoryOperations<T, ID> core) {
        this.core = core;
    }

    @Override
    public ReadResult<T> findById(ID id) {
        return withRead("findById", () -> {
            Optional<T> found = core.findByIdRaw(id);
            if (found.isPresent()) {
                return new Found<>(found.orElseThrow());
            }
            return new NotFound<>();
        });
    }

    @Override
    public ReadResult<Boolean> existsById(ID id) {
        return withRead("existsById", () -> new Found<>(core.existsByIdRaw(id)));
    }

    @Override
    public ReadResult<List<T>> findAll() {
        return withRead("findAll", () -> new Found<>(core.findAllRaw(Sort.unsorted(), null)));
    }

    @Override
    public ReadResult<List<T>> findAll(Sort sort) {
        return withRead("findAllSorted", () -> new Found<>(core.findAllRaw(sort, null)));
    }

    @Override
    public ReadResult<Page<T>> findAll(Pageable pageable) {
        return withRead("findAllPaged", () -> {
            long totalElements = core.countRaw();
            if (totalElements == 0 || pageable.getOffset() >= totalElements) {
                Page<T> page = new PageImpl<>(List.of(), pageable, totalElements);
                return new Found<>(page);
            }
            List<T> content = core.findAllRaw(pageable.getSort(), pageable);
            return new Found<>(new PageImpl<>(content, pageable, totalElements));
        });
    }

    @Override
    public ReadResult<List<T>> findAllById(Iterable<ID> ids) {
        return withRead("findAllById", () -> {
            List<T> results = new ArrayList<>();
            for (ID id : ids) {
                core.findByIdRaw(id).ifPresent(results::add);
            }
            return new Found<>(results);
        });
    }

    @Override
    public ReadResult<Long> count() {
        return withRead("count", () -> new Found<>(core.countRaw()));
    }

    private <R> ReadResult<R> withRead(String operation, ReadSupplier<ReadResult<R>> supplier) {
        try {
            return supplier.get();
        } catch (DataAccessException e) {
            return new Error<>("Read operation '" + operation + "' failed.", e);
        }
    }
}
