package io.github.auspis.fluentrepo4j.test.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Column(name = "user_name")
    private String name;

    private String email;
    
    private Integer age;


    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

}
