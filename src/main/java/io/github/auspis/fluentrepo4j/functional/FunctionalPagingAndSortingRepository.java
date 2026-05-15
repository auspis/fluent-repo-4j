package io.github.auspis.fluentrepo4j.functional;

import io.github.auspis.fluentrepo4j.functional.read.ReadResult;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

@NoRepositoryBean
public interface FunctionalPagingAndSortingRepository<T, ID> extends Repository<T, ID> {

    ReadResult<List<T>> findAll(Sort sort);

    ReadResult<Page<T>> findAll(Pageable pageable);
}
