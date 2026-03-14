package io.github.auspis.fluentrepo4j.test.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Example entity for testing repository operations.
 * Uses Jakarta Persistence annotations for mapping.
 */
@Table(name = "users")
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@AllArgsConstructor(access = lombok.AccessLevel.PUBLIC)
@Data
public class User {

    @Id
    private Long id;

    private String name;

    private String email;

    private Integer age;

    private Boolean active;

    private LocalDate birthdate;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "address")
    private String placeOfResidence;

    private String preferences;

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public User(String name, String email, Integer age) {
        this.name = name;
        this.email = email;
        this.age = age;
        active = true;
        createdAt = LocalDateTime.now();
        birthdate = LocalDate.now().minusYears(age);
        placeOfResidence = "Unknown";
        preferences = "{}";
    }

    public User withId(long id) {
        return new User(
                id,
                this.name,
                this.email,
                this.age,
                this.active,
                this.birthdate,
                this.createdAt,
                this.placeOfResidence,
                this.preferences);
    }
}
