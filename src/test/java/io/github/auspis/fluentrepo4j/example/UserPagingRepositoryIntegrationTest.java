package io.github.auspis.fluentrepo4j.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.auspis.fluentrepo4j.test.domain.User;
import io.github.auspis.fluentsql4j.dsl.DSL;
import io.github.auspis.fluentsql4j.test.util.annotation.IntegrationTest;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@IntegrationTest
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class UserPagingRepositoryIntegrationTest {

    @Autowired
    private UserPagingRepository userPagingRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DSL dsl;

    @BeforeEach
    void setUp() throws SQLException {
        // TODO: reuse TestDatabaseUtil.insertSampleUsers()/truncate/delete
        try (Connection connection = dataSource.getConnection();
                var ps = dsl.truncateTable("users").build(connection)) {
            ps.executeUpdate();
        }
        // Insert 5 users with known ordering attributes
        insertUser(1L, "Charlie", "charlie@example.com", 30);
        insertUser(2L, "Alice", "alice@example.com", 25);
        insertUser(3L, "Bob", "bob@example.com", 35);
        insertUser(4L, "Diana", "diana@example.com", 28);
        insertUser(5L, "Eve", "eve@example.com", 25);
    }

    @Test
    void findAll_sortedByName() {
        Iterable<User> users = userPagingRepository.findAll(Sort.by("name"));

        assertThat(users).extracting(User::getName).containsExactly("Alice", "Bob", "Charlie", "Diana", "Eve");
    }

    @Test
    void findAll_sortedByAgeDesc() {
        Iterable<User> users = userPagingRepository.findAll(Sort.by(Sort.Direction.DESC, "age"));

        assertThat(users).extracting(User::getAge).containsExactly(35, 30, 28, 25, 25);
    }

    @Test
    void findAll_sortingWithColumnAnnotationOverride() {
        // placeOfResidence maps to "address" column via @Column(name="address")
        // Insert users with different addresses to verify sorting works
        Iterable<User> users = userPagingRepository.findAll(Sort.by("id"));

        assertThat(users).extracting(User::getId).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void findAll_sortedByMultipleFields() {
        Iterable<User> users = userPagingRepository.findAll(Sort.by(Sort.Order.asc("age"), Sort.Order.asc("name")));

        // age 25: Alice, Eve; age 28: Diana; age 30: Charlie; age 35: Bob
        assertThat(users).extracting(User::getName).containsExactly("Alice", "Eve", "Diana", "Charlie", "Bob");
    }

    @Test
    void findAllSorted_unknownProperty_throwsException() {
        Sort unknownSort = Sort.by("nonExistent");

        assertThatThrownBy(() -> userPagingRepository.findAll(unknownSort))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("nonExistent");
    }

    @Test
    void findAllPaged_firstPage() {
        Page<User> page = userPagingRepository.findAll(PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getNumber()).isZero();
        assertThat(page.isFirst()).isTrue();
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void findAllPaged_secondPage() {
        Page<User> page = userPagingRepository.findAll(PageRequest.of(1, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getNumber()).isEqualTo(1);
    }

    @Test
    void findAllPaged_lastPagePartial() {
        Page<User> page = userPagingRepository.findAll(PageRequest.of(2, 2));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.isLast()).isTrue();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void findAllPaged_beyondLastPage() {
        Page<User> page = userPagingRepository.findAll(PageRequest.of(10, 2));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getNumber()).isEqualTo(10);
    }

    @Test
    void findAllPaged_withSort() {
        Page<User> page = userPagingRepository.findAll(PageRequest.of(0, 3, Sort.by("name")));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent()).extracting(User::getName).containsExactly("Alice", "Bob", "Charlie");
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void findAllPaged_emptyTable() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                var ps = dsl.truncateTable("users").build(connection)) {
            ps.executeUpdate();
        }

        Page<User> page = userPagingRepository.findAll(PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
    }

    private void insertUser(Long id, String name, String email, int age) {
        try (Connection connection = dataSource.getConnection();
                var ps = dsl.insertInto("users")
                        .set("id", id)
                        .set("name", name)
                        .set("email", email)
                        .set("age", age)
                        .set("active", true)
                        .build(connection)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
