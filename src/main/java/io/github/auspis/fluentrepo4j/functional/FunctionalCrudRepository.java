package io.github.auspis.fluentrepo4j.functional;

import io.github.auspis.fluentrepo4j.functional.read.FunctionalReadRepository;
import io.github.auspis.fluentrepo4j.functional.write.FunctionalWriteRepository;

import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface FunctionalCrudRepository<T, ID>
        extends FunctionalReadRepository<T, ID>, FunctionalWriteRepository<T, ID> {}
