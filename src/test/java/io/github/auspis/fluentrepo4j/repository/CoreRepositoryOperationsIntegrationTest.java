package io.github.auspis.fluentrepo4j.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.auspis.fluentrepo4j.connection.FluentConnectionProvider;
import io.github.auspis.fluentrepo4j.dialect.DialectDetector;
import io.github.auspis.fluentrepo4j.example.TestApplication;
import io.github.auspis.fluentrepo4j.mapping.FluentEntityInformation;
import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.dsl.DSLRegistry;
import io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest;
import io.github.auspis.fluentsql4j.test.util.database.TestDatabaseUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@IntegrationTest
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
class CoreRepositoryOperationsIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private CoreRepositoryOperations<User, Long> core;

    @BeforeEach
    void setUp() throws SQLException {
        FluentConnectionProvider connectionProvider = new FluentConnectionProvider(dataSource);
        DSL dsl = DialectDetector.detect(dataSource, DSLRegistry.createWithServiceLoader());
        core = new CoreRepositoryOperations<>(new FluentEntityInformation<>(User.class), connectionProvider, dsl);
        try (Connection connection = dataSource.getConnection()) {
            TestDatabaseUtil.H2.truncateUsers(connection);
            TestDatabaseUtil.H2.insertSampleUsers(connection);
        }
    }

    @Test
    void findByIdRaw_existingAndMissing() {
        Optional<User> existing = core.findByIdRaw(1L);
        Optional<User> missing = core.findByIdRaw(999L);

        assertThat(existing).isPresent();
        assertThat(existing.orElseThrow().getName()).isEqualTo("John Doe");
        assertThat(missing).isEmpty();
    }

    @Test
    void countRaw_returnsSeededCount() {
        assertThat(core.countRaw()).isEqualTo(10);
    }

    @Test
    void deleteByIdRaw_returnsAffectedRows() {
        assertThat(core.deleteByIdRaw(1L)).isEqualTo(1);
        assertThat(core.deleteByIdRaw(999L)).isZero();
    }

    @Test
    void deleteAllRaw_returnsAffectedRows() {
        assertThat(core.deleteAllRaw()).isEqualTo(10);
        assertThat(core.countRaw()).isZero();
    }

    @Test
    void findAllRaw_supportsSortingAndPaging() {
        List<User> sorted = core.findAllRaw(Sort.by(Sort.Direction.ASC, "name"), null);
        List<User> page = core.findAllRaw(Sort.by(Sort.Direction.ASC, "name"), PageRequest.of(0, 3));

        assertThat(sorted).hasSize(10);
        assertThat(page).hasSize(3);
        assertThat(page.get(0).getName()).isEqualTo(sorted.get(0).getName());
        assertThat(page.get(1).getName()).isEqualTo(sorted.get(1).getName());
        assertThat(page.get(2).getName()).isEqualTo(sorted.get(2).getName());
    }

    @Test
    void update_throwsOptimisticLockingWhenEntityMissing() {
        User user = new User("Ghost", "ghost@example.com").withId(999L);

        assertThatThrownBy(() -> core.update(user)).isInstanceOf(OptimisticLockingFailureException.class);
    }
}
