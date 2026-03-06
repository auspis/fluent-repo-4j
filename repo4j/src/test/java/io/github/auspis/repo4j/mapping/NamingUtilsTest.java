package io.github.auspis.repo4j.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NamingUtilsTest {

    @Test
    void toSnakeCase_simpleCase() {
        assertThat(NamingUtils.toSnakeCase("firstName")).isEqualTo("first_name");
    }

    @Test
    void toSnakeCase_allLower() {
        assertThat(NamingUtils.toSnakeCase("name")).isEqualTo("name");
    }

    @Test
    void toSnakeCase_multipleWords() {
        assertThat(NamingUtils.toSnakeCase("createdAtTime")).isEqualTo("created_at_time");
    }

    @Test
    void toSnakeCase_startsWithUpper() {
        assertThat(NamingUtils.toSnakeCase("UserProfile")).isEqualTo("user_profile");
    }

    @Test
    void toSnakeCase_alreadySnake() {
        assertThat(NamingUtils.toSnakeCase("already_snake")).isEqualTo("already_snake");
    }

    @Test
    void toSnakeCase_singleChar() {
        assertThat(NamingUtils.toSnakeCase("A")).isEqualTo("a");
    }

    @Test
    void toSnakeCase_consecutiveUppercase() {
        assertThat(NamingUtils.toSnakeCase("HTMLParser")).isEqualTo("html_parser");
    }

    @Test
    void toSnakeCase_nullInput() {
        assertThat(NamingUtils.toSnakeCase(null)).isNull();
    }

    @Test
    void toSnakeCase_emptyInput() {
        assertThat(NamingUtils.toSnakeCase("")).isEmpty();
    }
}
