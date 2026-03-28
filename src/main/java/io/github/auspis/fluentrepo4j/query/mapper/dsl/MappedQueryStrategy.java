package io.github.auspis.fluentrepo4j.query.mapper.dsl;

import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentrepo4j.query.OrderByClause;
import io.github.auspis.fluentrepo4j.query.PageWindow;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentrepo4j.query.QueryRuntimeParams;
import io.github.auspis.fluentrepo4j.query.runtime.ExecutableQuery;
import io.github.auspis.fluentsql4j.ast.core.predicate.Predicate;
import io.github.auspis.fluentsql4j.ast.dql.clause.Sorting;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.clause.LogicalCombinator;
import io.github.auspis.fluentsql4j.dsl.delete.DeleteBuilder;
import io.github.auspis.fluentsql4j.dsl.select.OrderByBuilder;
import io.github.auspis.fluentsql4j.dsl.select.SelectBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

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

    ExecutableQuery<T> create(QueryDescriptor descriptor, Object[] args, QueryRuntimeParams runtimeParams);

    public static class SelectMappedQueryStrategy<T, ID> implements MappedQueryStrategy<T, ID> {

        private final DSL dsl;
        private final PropertyMetadataProvider<T, ID> metadataProvider;
        private final BiFunction<DSL, PropertyMetadataProvider<T, ID>, SelectBuilder> selectBuilderFunction;
        private final Function<SelectBuilder, ExecutableQuery<T>> queryWrapper;
        private final PredicateDescriptorMapper predicateMapper = new PredicateDescriptorMapper();

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
        public ExecutableQuery<T> create(QueryDescriptor descriptor, Object[] args, QueryRuntimeParams runtimeParams) {
            SelectBuilder selectBuilder = selectBuilderFunction.apply(dsl, metadataProvider);

            // Apply WHERE predicates
            Predicate where = predicateMapper.map(descriptor.predicateDescriptor(), metadataProvider, args);
            selectBuilder = selectBuilder.addWhereCondition(where, LogicalCombinator.AND);

            // Collect order-by clauses: static (from method name) + runtime (from QueryRuntimeParams)
            List<OrderByClause> orderByClauses = new ArrayList<>(descriptor.orderBy());
            orderByClauses.addAll(runtimeParams.runtimeSort());

            // Apply ORDER BY and FETCH/OFFSET — builder is fully configured here
            PageWindow pageWindow = runtimeParams.pageWindow();
            if (!orderByClauses.isEmpty()) {
                OrderByBuilder orderByBuilder = selectBuilder.orderBy();
                for (OrderByClause clause : orderByClauses) {
                    orderByBuilder = clause.direction() == Sorting.SortOrder.ASC
                            ? orderByBuilder.asc(clause.columnName())
                            : orderByBuilder.desc(clause.columnName());
                }
                if (pageWindow != null) {
                    selectBuilder = orderByBuilder.fetch(pageWindow.pageSize()).offset(pageWindow.offset());
                } else if (descriptor.maxResults() != null) {
                    selectBuilder = orderByBuilder.fetch(descriptor.maxResults());
                } else {
                    selectBuilder = orderByBuilder.done();
                }
            } else {
                if (pageWindow != null) {
                    selectBuilder = selectBuilder.fetch(pageWindow.pageSize()).offset(pageWindow.offset());
                } else if (descriptor.maxResults() != null) {
                    selectBuilder = selectBuilder.fetch(descriptor.maxResults());
                }
            }

            return queryWrapper.apply(selectBuilder);
        }
    }

    public static class DeleteMappedQueryStrategy<T, ID> implements MappedQueryStrategy<T, ID> {

        private final DSL dsl;
        private final PropertyMetadataProvider<T, ID> metadataProvider;
        private final PredicateDescriptorMapper predicateMapper = new PredicateDescriptorMapper();

        private DeleteMappedQueryStrategy(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
            this.dsl = dsl;
            this.metadataProvider = metadataProvider;
        }

        @Override
        public ExecutableQuery<T> create(QueryDescriptor descriptor, Object[] args, QueryRuntimeParams runtimeParams) {
            String table = metadataProvider.getTableName();
            DeleteBuilder delete = dsl.deleteFrom(table);

            Predicate where = predicateMapper.map(descriptor.predicateDescriptor(), metadataProvider, args);
            delete = delete.addWhereCondition(where, LogicalCombinator.AND);

            return new ExecutableQuery.DeleteQuery<>(delete);
        }
    }
}
