package io.github.auspis.fluentrepo4j.repository;

import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.data.domain.Persistable;

import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.mapping.IdGenerationStrategy;

/**
 * Determines the {@link SaveAction} to perform for a given entity.
 * <p>
 * Decision logic:
 * <ol>
 *   <li>If the entity implements {@link Persistable}, delegates to {@link Persistable#isNew()}
 *       — developer has full control, no database call is made.</li>
 *   <li>If the ID is {@code null}, the entity is new — the action depends on the
 *       {@link IdGenerationStrategy}.</li>
 *   <li>If the ID is non-null, checks the database via {@code existsById}:
 *       <ul>
 *         <li>If it exists → {@link SaveAction#UPDATE}</li>
 *         <li>If it doesn't exist and strategy is {@code PROVIDED} → {@link SaveAction#INSERT_PROVIDED_ID}</li>
 *         <li>If it doesn't exist and strategy is {@code IDENTITY} → {@link SaveAction#ERROR}
 *             (an IDENTITY entity with a non-null ID that isn't in the DB is inconsistent)</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * @param <T>  the entity type
 * @param <ID> the entity identifier type
 */
public class SaveDecisionResolver<T, ID> implements Function<T, SaveAction> {

    private final FluentEntityInformation<T, ID> entityInfo;
    private final Predicate<ID> existsById;

    public SaveDecisionResolver(FluentEntityInformation<T, ID> entityInfo, Predicate<ID> existsById) {
        this.entityInfo = entityInfo;
        this.existsById = existsById;
    }

    @Override
    public SaveAction apply(T entity) {
        // 1. Persistable path: developer has full control, no DB call
        if (entity instanceof Persistable<?> persistable) {
            if (persistable.isNew()) {
                return insertActionFor(entityInfo.getIdGenerationStrategy());
            }
            return SaveAction.UPDATE;
        }

        // 2. Standard path: id == null → new entity
        ID id = entityInfo.getId(entity);
        if (id == null) {
            return insertActionFor(entityInfo.getIdGenerationStrategy());
        }

        // 3. id != null: check DB
        if (existsById.test(id)) {
            return SaveAction.UPDATE;
        }

        // 4. id != null, not in DB: depends on strategy
        return switch (entityInfo.getIdGenerationStrategy()) {
            case PROVIDED -> SaveAction.INSERT_PROVIDED_ID;
            case IDENTITY -> SaveAction.ERROR;
        };
    }

    private SaveAction insertActionFor(IdGenerationStrategy strategy) {
        return switch (strategy) {
            case PROVIDED -> SaveAction.INSERT_PROVIDED_ID;
            case IDENTITY -> SaveAction.INSERT_AUTO_ID;
        };
    }
}
