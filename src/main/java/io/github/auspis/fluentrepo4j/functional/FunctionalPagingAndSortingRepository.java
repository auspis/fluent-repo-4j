package io.github.auspis.fluentrepo4j.functional;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Functional counterpart of Spring Data's
 * {@link org.springframework.data.repository.PagingAndSortingRepository}.
 *
 * <p>Provides sorted and paginated retrieval operations whose results are wrapped
 * in {@link RepositoryResult}. This interface is <b>mutually exclusive</b> with
 * {@code PagingAndSortingRepository}: a repository interface must extend one
 * or the other, not both.
 *
 * <p>Typically combined with {@link FunctionalCrudRepository} in the same
 * repository definition.
 *
 * @param <T>  the entity type
 * @param <ID> the entity identifier type
 * @see RepositoryResult
 * @see FunctionalCrudRepository
 */
@NoRepositoryBean
public interface FunctionalPagingAndSortingRepository<T, ID> extends Repository<T, ID> {

    /**
     * Returns all entities sorted by the given options.
     *
     * @param sort the {@link Sort} specification, can be {@link Sort#unsorted()}, must not be {@literal null}
     * @return a {@link RepositoryResult} carrying the sorted entities
     */
    RepositoryResult<List<T>> findAll(Sort sort);

    /**
     * Returns a {@link Page} of entities meeting the paging restriction.
     *
     * @param pageable the pageable to request a paged result, can be {@link Pageable#unpaged()},
     *                 must not be {@literal null}
     * @return a {@link RepositoryResult} carrying the page of entities
     */
    RepositoryResult<Page<T>> findAll(Pageable pageable);
}
