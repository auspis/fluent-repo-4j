package io.github.auspis.fluentrepo4j.query.mapper.dsl;

import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentrepo4j.query.OrderByClause;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentrepo4j.query.runtime.ExecutableQuery;
import io.github.auspis.fluentsql4j.ast.core.predicate.NullPredicate;
import io.github.auspis.fluentsql4j.ast.core.predicate.Predicate;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.clause.LogicalCombinator;
import io.github.auspis.fluentsql4j.dsl.delete.DeleteBuilder;
import io.github.auspis.fluentsql4j.dsl.select.OrderByBuilder;
import io.github.auspis.fluentsql4j.dsl.select.SelectBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public interface MappedQueryStrategy<T, ID> {

    public static <T, ID> MappedQueryStrategy<T, ID> select(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
        return new SelectMappedQueryStrategy<>(
                dsl,
                metadataProvider,
                (d, m) -> d.selectAll().from(m.getTableName()),
                ExecutableQuery.EntitySelectQuery::new);
    }

    public static <T, ID> MappedQueryStrategy<T, ID> delete(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
        return new DeleteMappedQueryStrategy<>(dsl, metadataProvider);
    }

    public static <T, ID> MappedQueryStrategy<T, ID> count(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
        return new SelectMappedQueryStrategy<>(
                dsl,
                metadataProvider,
                (d, m) -> d.select().countStar().from(m.getTableName()),
                ExecutableQuery.CountQuery::new);
    }

    public static <T, ID> MappedQueryStrategy<T, ID> exists(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
        return new SelectMappedQueryStrategy<>(
                dsl,
                metadataProvider,
                (d, m) -> d.select().countStar().from(m.getTableName()),
                ExecutableQuery.ExistsQuery::new);
    }

    ExecutableQuery<T> create(QueryDescriptor descriptor, Object[] args);

    public static class SelectMappedQueryStrategy<T, ID> implements MappedQueryStrategy<T, ID> {

        private final DSL dsl;
        private final PropertyMetadataProvider<T, ID> metadataProvider;
        private final BiFunction<DSL, PropertyMetadataProvider<T, ID>, SelectBuilder> selectBuilderFunction;
        private final Function<SelectBuilder, ExecutableQuery<T>> queryWrapper;

        private SelectMappedQueryStrategy(
                DSL dsl,
                PropertyMetadataProvider<T, ID> metadataProvider,
                BiFunction<DSL, PropertyMetadataProvider<T, ID>, SelectBuilder> selectBuilderSupplier,
                Function<SelectBuilder, ExecutableQuery<T>> queryWrapper) {
            this.dsl = dsl;
            this.metadataProvider = metadataProvider;
            this.selectBuilderFunction = selectBuilderSupplier;
            this.queryWrapper = queryWrapper;
        }

        @Override
        public ExecutableQuery<T> create(QueryDescriptor descriptor, Object[] args) {
            SelectBuilder selectBuilder = selectBuilderFunction.apply(dsl, metadataProvider);

            // Apply WHERE predicates
            Predicate where = descriptor.predicateDescriptor().toPredicate(metadataProvider, args);
            if (where != null && !(where instanceof NullPredicate)) {
                selectBuilder = selectBuilder.addWhereCondition(where, LogicalCombinator.AND);
            }

            // Collect order-by clauses: static (from method name) + runtime (Sort / Pageable)
            List<OrderByClause> orderByClauses = new ArrayList<>(descriptor.orderBy());
            Sort runtimeSort = extractSort(descriptor, args);
            if (runtimeSort != null && runtimeSort.isSorted()) {
                for (Sort.Order order : runtimeSort) {
                    String column = resolveOrderColumn(order.getProperty());
                    orderByClauses.add(new OrderByClause(column, order.getDirection()));
                }
            }

            // Apply ORDER BY and FETCH/OFFSET — builder is fully configured here
            if (!orderByClauses.isEmpty()) {
                OrderByBuilder orderByBuilder = selectBuilder.orderBy();
                for (OrderByClause clause : orderByClauses) {
                    orderByBuilder = clause.direction() == Sort.Direction.ASC
                            ? orderByBuilder.asc(clause.columnName())
                            : orderByBuilder.desc(clause.columnName());
                }
                Pageable pageable = extractPageable(descriptor, args);
                if (pageable != null && pageable.isPaged()) {
                    selectBuilder = orderByBuilder.fetch(pageable.getPageSize()).offset(pageable.getOffset());
                } else if (descriptor.maxResults() != null) {
                    selectBuilder = orderByBuilder.fetch(descriptor.maxResults());
                } else {
                    selectBuilder = orderByBuilder.done();
                }
            } else {
                Pageable pageable = extractPageable(descriptor, args);
                if (pageable != null && pageable.isPaged()) {
                    selectBuilder = selectBuilder.fetch(pageable.getPageSize()).offset(pageable.getOffset());
                } else if (descriptor.maxResults() != null) {
                    selectBuilder = selectBuilder.fetch(descriptor.maxResults());
                }
            }

            return queryWrapper.apply(selectBuilder);
        }

        private Sort extractSort(QueryDescriptor descriptor, Object[] args) {
            if (descriptor.pageableParamIndex() >= 0 && args != null && args.length > descriptor.pageableParamIndex()) {
                Object arg = args[descriptor.pageableParamIndex()];
                if (arg instanceof Pageable p) {
                    return p.getSort();
                }
            }
            if (descriptor.sortParamIndex() >= 0 && args != null && args.length > descriptor.sortParamIndex()) {
                Object arg = args[descriptor.sortParamIndex()];
                if (arg instanceof Sort s) {
                    return s;
                }
            }
            return null;
        }

        private Pageable extractPageable(QueryDescriptor descriptor, Object[] args) {
            if (descriptor.pageableParamIndex() >= 0 && args != null && args.length > descriptor.pageableParamIndex()) {
                Object arg = args[descriptor.pageableParamIndex()];
                if (arg instanceof Pageable p) {
                    return p;
                }
            }
            return null;
        }

        private String resolveOrderColumn(String propertyOrColumn) {
            try {
                return metadataProvider.resolveColumn(propertyOrColumn);
            } catch (IllegalArgumentException e) {
                return propertyOrColumn; // already a column name
            }
        }
    }

    public static class DeleteMappedQueryStrategy<T, ID> implements MappedQueryStrategy<T, ID> {

        private final DSL dsl;
        private final PropertyMetadataProvider<T, ID> metadataProvider;

        private DeleteMappedQueryStrategy(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
            this.dsl = dsl;
            this.metadataProvider = metadataProvider;
        }

        @Override
        public ExecutableQuery<T> create(QueryDescriptor descriptor, Object[] args) {
            String table = metadataProvider.getTableName();
            DeleteBuilder delete = dsl.deleteFrom(table);

            Predicate where = descriptor.predicateDescriptor().toPredicate(metadataProvider, args);
            if (where != null && !(where instanceof NullPredicate)) {
                delete = delete.addWhereCondition(where, LogicalCombinator.AND);
            }

            return new ExecutableQuery.DeleteQuery<>(delete);
        }
    }
}
