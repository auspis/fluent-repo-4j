package io.github.auspis.fluentrepo4j.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.auspis.fluentrepo4j.functional.RepositoryResult.Failure;
import io.github.auspis.fluentrepo4j.functional.RepositoryResult.Success;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RepositoryResultTest {

    @Nested
    class SuccessTests {

        @Test
        void wrapsNonNullValue() {
            RepositoryResult<String> result = new Success<>("hello");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isFailure()).isFalse();
            assertThat(result.orElseThrow()).isEqualTo("hello");
        }

        @Test
        void rejectsNullValue() {
            assertThatThrownBy(() -> new Success<>(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }
    }

    @Nested
    class FailureTests {

        @Test
        void wrapsMessage() {
            RepositoryResult<String> result = new Failure<>("something went wrong");
            assertThat(result.isFailure()).isTrue();
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void wrapsMessageAndCause() {
            RuntimeException cause = new RuntimeException("root");
            RepositoryResult<String> result = new Failure<>("wrapped", cause);
            assertThat(result.isFailure()).isTrue();
            assertThat(((Failure<String>) result).cause()).isSameAs(cause);
        }

        @Test
        void rejectsNullMessage() {
            assertThatThrownBy(() -> new Failure<>(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsBlankMessage() {
            assertThatThrownBy(() -> new Failure<>("   ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Combinators {

        @Test
        void mapTransformsSuccess() {
            RepositoryResult<Integer> result = new Success<>("hello").map(String::length);
            assertThat(result.orElseThrow()).isEqualTo(5);
        }

        @Test
        void mapPreservesFailure() {
            RepositoryResult<Integer> result = new Failure<String>("fail").map(String::length);
            assertThat(result.isFailure()).isTrue();
        }

        @Test
        void foldOnSuccess() {
            String folded = new Success<>("hello").fold(v -> "ok:" + v, f -> "err:" + f.message());
            assertThat(folded).isEqualTo("ok:hello");
        }

        @Test
        void foldOnFailure() {
            String folded = new Failure<String>("oops").fold(v -> "ok:" + v, f -> "err:" + f.message());
            assertThat(folded).isEqualTo("err:oops");
        }

        @Test
        void peekExecutesOnSuccess() {
            StringBuilder sb = new StringBuilder();
            RepositoryResult<String> result = new Success<>("data");
            RepositoryResult<String> peeked = result.peek(sb::append);
            assertThat(sb).hasToString("data");
            assertThat(peeked).isSameAs(result);
        }

        @Test
        void peekSkipsOnFailure() {
            StringBuilder sb = new StringBuilder();
            RepositoryResult<String> result = new Failure<>("err");
            RepositoryResult<String> peeked = result.peek(sb::append);
            assertThat(sb).isEmpty();
            assertThat(peeked).isSameAs(result);
        }

        @Test
        void orElseReturnsValueOnSuccess() {
            assertThat(new Success<>("present").orElse("default")).isEqualTo("present");
        }

        @Test
        void orElseReturnsDefaultOnFailure() {
            assertThat(new Failure<String>("err").orElse("default")).isEqualTo("default");
        }

        @Test
        void orElseThrowRethrowsFailure() {
            Failure<String> result = new Failure<>("boom", new RuntimeException("cause"));
            assertThatThrownBy(result::orElseThrow)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("boom")
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }
}
