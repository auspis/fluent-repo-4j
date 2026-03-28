package io.github.auspis.fluentrepo4j.query.mapper.dsl;

import io.github.auspis.fluentrepo4j.meta.PropertyMetadataProvider;
import io.github.auspis.fluentrepo4j.query.QueryDescriptor;
import io.github.auspis.fluentrepo4j.query.QueryOperation;
import io.github.auspis.fluentrepo4j.query.runtime.ExecutableQuery;
import io.github.auspis.fluentsql4j.ast.core.expression.function.string.UnaryString;
import io.github.auspis.fluentsql4j.ast.core.predicate.Between;
import io.github.auspis.fluentsql4j.ast.core.predicate.Comparison;
import io.github.auspis.fluentsql4j.ast.core.predicate.In;
import io.github.auspis.fluentsql4j.ast.core.predicate.IsNotNull;
import io.github.auspis.fluentsql4j.ast.core.predicate.IsNull;
import io.github.auspis.fluentsql4j.ast.core.predicate.Like;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.delete.DeleteBuilder;
import io.github.auspis.fluentsql4j.dsl.select.SelectBuilder;
import io.github.auspis.fluentsql4j.dsl.util.LiteralUtil;

import java.util.Map;
import java.util.function.Function;

/**
 * Converts a {@link QueryDescriptor} and a set of runtime method arguments into a
 * fluent-sql-4j {@link SelectBuilder} or {@link DeleteBuilder}, ready to be
 * compiled into a {@link java.sql.PreparedStatement}.
 *
 * <p>All predicate values are bound as prepared-statement parameters via
 * {@link LiteralUtil#createLiteral(Object)} or the AST predicate constructors -
 * never via string concatenation - to prevent SQL injection.
 *
 * <p>Operator mapping:
 * <ul>
 *   <li>EQUALS / NOT_EQUALS / comparisons - {@link Comparison}</li>
 *   <li>IS_NULL / IS_NOT_NULL - {@link IsNull} / {@link IsNotNull}</li>
 *   <li>LIKE / variants - {@link Like} (pattern with wildcards bound as param)</li>
 *   <li>IgnoreCase - {@code LOWER(col)} via {@link UnaryString#lower}</li>
 *   <li>BETWEEN - {@link Between}</li>
 *   <li>IN / NOT_IN - {@link In} / {@code NOT(In)}</li>
 *   <li>TRUE / FALSE - {@link Comparison#eq} with Boolean literal</li>
 * </ul>
 *
 * @param <T>  entity type
 * @param <ID> identifier type
 */
public final class QueryDescriptorToDslMapper<T, ID> {

    private Function<QueryOperation, MappedQueryStrategy<T, ID>> buildStrategies;

    public QueryDescriptorToDslMapper(DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
        this.buildStrategies = initBuildStrategies(dsl, metadataProvider);
    }

    /**
     * Builds and returns an {@link ExecutableQuery} that is ready to execute
     * against a database connection.
     *
     * @param descriptor the parsed query descriptor (cached per method)
     * @param args       the runtime method arguments (in method-signature order)
     * @return an {@link ExecutableQuery} ready to execute
     */
    public ExecutableQuery<T> map(QueryDescriptor descriptor, Object[] args) {
        return buildStrategies.apply(descriptor.operation()).create(descriptor, args);
    }

    private Function<QueryOperation, MappedQueryStrategy<T, ID>> initBuildStrategies(
            DSL dsl, PropertyMetadataProvider<T, ID> metadataProvider) {
        MappedQueryStrategy<T, ID> defaultBuildStrategy = MappedQueryStrategy.select(dsl, metadataProvider);
        Map<QueryOperation, MappedQueryStrategy<T, ID>> mappings = Map.of(
                QueryOperation.EXISTS, MappedQueryStrategy.exists(dsl, metadataProvider),
                QueryOperation.COUNT, MappedQueryStrategy.count(dsl, metadataProvider),
                QueryOperation.DELETE, MappedQueryStrategy.delete(dsl, metadataProvider));
        return op -> mappings.getOrDefault(op, defaultBuildStrategy);
    }
}
