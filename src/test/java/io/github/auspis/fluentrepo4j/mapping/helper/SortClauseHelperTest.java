package io.github.auspis.fluentrepo4j.mapping.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.mapping.helper.SortClauseHelper.ColumnOrder;
import io.github.auspis.fluentrepo4j.test.domain.User;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Sort;

class SortClauseHelperTest {

    private final FluentEntityInformation<User, Long> entityInfo = new FluentEntityInformation<>(User.class);
    private final SortClauseHelper helper = new SortClauseHelper(entityInfo);

    @Test
    void unsorted_returnsEmptyList() {
        List<ColumnOrder> result = helper.resolve(Sort.unsorted());

        assertThat(result).isEmpty();
    }

    @Test
    void singleField_resolvesToSnakeCaseColumn() {
        List<ColumnOrder> result = helper.resolve(Sort.by("name"));

        assertThat(result).containsExactly(new ColumnOrder("name", Sort.Direction.ASC));
    }

    @Test
    void columnAnnotationOverride_resolvesToAnnotatedName() {
        List<ColumnOrder> result = helper.resolve(Sort.by("placeOfResidence"));

        assertThat(result).containsExactly(new ColumnOrder("address", Sort.Direction.ASC));
    }

    @Test
    void idField_resolvesCorrectly() {
        List<ColumnOrder> result = helper.resolve(Sort.by(Sort.Direction.DESC, "id"));

        assertThat(result).containsExactly(new ColumnOrder("id", Sort.Direction.DESC));
    }

    @Test
    void multipleFields_preservesOrder() {
        Sort sort = Sort.by(Sort.Order.desc("age"), Sort.Order.asc("name"));

        List<ColumnOrder> result = helper.resolve(sort);

        assertThat(result)
                .containsExactly(
                        new ColumnOrder("age", Sort.Direction.DESC), new ColumnOrder("name", Sort.Direction.ASC));
    }

    @Test
    void unknownProperty_throwsException() {
        Sort sort = Sort.by("nonExistentField");

        assertThatThrownBy(() -> helper.resolve(sort))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("nonExistentField");
    }

    @Test
    void snakeCaseConvention_resolvesCorrectly() {
        List<ColumnOrder> result = helper.resolve(Sort.by("birthdate"));

        assertThat(result).containsExactly(new ColumnOrder("birthdate", Sort.Direction.ASC));
    }

    @Test
    void columnAnnotationCreatedAt_resolvesToAnnotatedName() {
        List<ColumnOrder> result = helper.resolve(Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(result).containsExactly(new ColumnOrder("createdAt", Sort.Direction.DESC));
    }
}
