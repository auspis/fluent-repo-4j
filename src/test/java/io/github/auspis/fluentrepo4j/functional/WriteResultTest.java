package io.github.auspis.fluentrepo4j.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.auspis.fluentrepo4j.functional.write.WriteResult;
import io.github.auspis.fluentrepo4j.functional.write.WriteResult.Error;
import io.github.auspis.fluentrepo4j.functional.write.WriteResult.Success;

import java.util.function.Function;
import org.junit.jupiter.api.Test;

class WriteResultTest {

    @Test
    void success() {
        WriteResult<String> result = new Success<>("hello");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow()).isEqualTo("hello");
    }

    @Test
    void successWithNullValue() {
        assertThatThrownBy(() -> new Success<>(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void errorWithBlankMessage() {
        assertThatThrownBy(() -> new Error<String>(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapSuccess() {
        WriteResult<Integer> mapped = new Success<>("abc").map(String::length);
        assertThat(mapped.orElseThrow()).isEqualTo(3);
    }

    @Test
    void mapError() {
        WriteResult<Integer> mapped = new Error<String>("err").map(String::length);
        assertThat(mapped).isInstanceOf(Error.class);
    }

    @Test
    void foldSuccess() {
        Function<String, String> onSuccess = v -> "success:" + v;
        Function<Error<String>, String> onError = f -> "error:" + f.message();
        String foldedSuccess = new Success<>("abc").fold(onSuccess, onError);

        assertThat(foldedSuccess).isEqualTo("success:abc");
    }

    @Test
    void foldError() {
        Function<String, String> onSuccess = v -> "success:" + v;
        Function<Error<String>, String> onError = f -> "error:" + f.message();
        String foldedError = new Error<String>("boom").fold(onSuccess, onError);

        assertThat(foldedError).isEqualTo("error:boom");
    }
}
