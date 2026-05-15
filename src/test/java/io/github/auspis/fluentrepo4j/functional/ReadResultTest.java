package io.github.auspis.fluentrepo4j.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.auspis.fluentrepo4j.functional.read.ReadResult;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Error;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.Found;
import io.github.auspis.fluentrepo4j.functional.read.ReadResult.NotFound;

import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ReadResultTest {

    @Test
    void found() {
        ReadResult<String> result = new Found<>("hello");
        assertThat(result.isFound()).isTrue();
        assertThat(result.orElseThrow()).isEqualTo("hello");
    }

    @Test
    void foundWithNullValue() {
        assertThatThrownBy(() -> new Found<>(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notFound() {
        ReadResult<String> result = new NotFound<>();
        assertThat(result.isNotFound()).isTrue();
        assertThat(result.orElse("default")).isEqualTo("default");
    }

    @Test
    void errorWithBlankMessage() {
        assertThatThrownBy(() -> new Error<String>(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Error message must not be null or blank");
    }

    @Test
    void mapFound() {
        ReadResult<Integer> foundMapped = new Found<>("abc").map(String::length);
        assertThat(foundMapped)
                .isInstanceOf(Found.class)
                .extracting(r -> ((Found<Integer>) r).value())
                .isEqualTo(3);
    }

    @Test
    void mapNotFound() {
        ReadResult<Integer> notFoundMapped = new NotFound<String>().map(String::length);
        assertThat(notFoundMapped).isInstanceOf(NotFound.class);
    }

    @Test
    void mapError() {
        ReadResult<Integer> errorMapped = new Error<String>("fail").map(String::length);
        assertThat(errorMapped).isInstanceOf(Error.class);
    }

    @Test
    void fold() {
        Function<String, String> onFound = v -> "found:" + v;
        Supplier<String> onNotFound = () -> "not found";
        Function<Error<String>, String> onError = f -> "error:" + f.message();

        String found = new Found<>("abc").fold(onFound, onNotFound, onError);
        String missing = new NotFound<String>().fold(onFound, onNotFound, onError);
        String failed = new Error<String>("boom").fold(onFound, onNotFound, onError);

        assertThat(found).isEqualTo("found:abc");
        assertThat(missing).isEqualTo("not found");
        assertThat(failed).isEqualTo("error:boom");
    }
}
