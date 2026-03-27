package io.github.auspis.fluentrepo4j.query.mapper.dsl;

import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentrepo4j.query.OrderByClause;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentsql4j.ast.core.predicate.NullPredicate;
import io.github.auspis.fluentsql4j.ast.core.predicate.Predicate;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.clause.LogicalCombinator;
import io.github.auspis.fluentsql4j.dsl.delete.DeleteBuilder;
import io.github.auspis.fluentsql4j.dsl.select.SelectBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public interface MappedQueryStrategy<T, ID> {

    public static <T, ID> MappedQueryStrategy<T, ID> select(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
        return new SelectMappedQueryStrategy<>(
                dsl, metadataProvider, (d, m) -> d.selectAll().from(m.getTableName()));
    }

    public static <T, ID> MappedQueryStrategy<T, ID> delete(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
        return new DeleteMappedQueryStrategy<>(dsl, metadataProvider);
    }

    public static <T, ID> MappedQueryStrategy<T, ID> count(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
        return new SelectMappedQueryStrategy<>(
                dsl, metadataProvider, (d, m) -> d.select().countStar().from(m.getTableName()));
    }

    public static <T, ID> MappedQueryStrategy<T, ID> exists(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
        return count(dsl, metadataProvider);
    }

    public MappedQuery create(QueryDescriptor descriptor, Object[] args);

    public static class SelectMappedQueryStrategy<T, ID> implements MappedQueryStrategy<T, ID> {

        private DSL dsl;
        private PropertyMetadataProvider<T, ID> metadataProvider;
        private BiFunction<DSL, PropertyMetadataProvider<T, ID>, SelectBuilder> selectBuilderFunction;

        private SelectMappedQueryStrategy(
                DSL dsl,
                PropertyMetadataProvider<T, ID> metadataProvider,
                BiFunction<DSL, PropertyMetadataProvider<T, ID>, SelectBuilder> selectBuilderSupplier) {
            this.dsl = dsl;
            this.metadataProvider = metadataProvider;
            this.selectBuilderFunction = selectBuilderSupplier;
        }

        @Override
        public MappedQuery create(QueryDescriptor descriptor, Object[] args) {
            SelectBuilder selectBuilder = selectBuilderFunction.apply(dsl, metadataProvider);

            // Apply WHERE predicates
            Predicate where = descriptor.criterion().toPredicate(metadataProvider, args);
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

            return new MappedQuery.SelectResult(selectBuilder, orderByClauses, descriptor, args);
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

        private String resolveOrderColumn(String propertyOrColumn) {
            try {
                return metadataProvider.resolveColumn(propertyOrColumn);
            } catch (IllegalArgumentException e) {
                return propertyOrColumn; // already a column name
            }
        }
    }

    public static class DeleteMappedQueryStrategy<T, ID> implements MappedQueryStrategy<T, ID> {

        private DSL dsl;
        private PropertyMetadataProvider<T, ID> metadataProvider;

        private DeleteMappedQueryStrategy(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
            this.dsl = dsl;
            this.metadataProvider = metadataProvider;
        }

        @Override
        public MappedQuery create(QueryDescriptor descriptor, Object[] args) {
            String table = metadataProvider.getTableName();
            DeleteBuilder delete = dsl.deleteFrom(table);

            Predicate where = descriptor.criterion().toPredicate(metadataProvider, args);
            if (where != null && !(where instanceof NullPredicate)) {
                delete = delete.addWhereCondition(where, LogicalCombinator.AND);
            }

            return new MappedQuery.DeleteResult(delete);
        }
    }
}
