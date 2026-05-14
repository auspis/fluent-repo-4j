package io.github.auspis.fluentrepo4j.functional.write;

import java.util.function.Consumer;
import java.util.function.Function;

public sealed interface WriteResult<T> {

    record Success<T>(T value) implements WriteResult<T> {
        public Success {
            if (value == null) {
                throw new IllegalArgumentException("Success value must not be null");
            }
        }
    }

    record Error<T>(String message, Throwable cause) implements WriteResult<T> {
        public Error {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("Error message must not be null or blank");
            }
        }

        public Error(String message) {
            this(message, null);
        }
    }

    default <U> WriteResult<U> map(Function<T, U> mapper) {
        return switch (this) {
            case Success<T>(T value) -> new Success<>(mapper.apply(value));
            case Error<T>(String message, Throwable cause) -> new Error<>(message, cause);
        };
    }

    default <U> U fold(Function<T, U> onSuccess, Function<Error<T>, U> onError) {
        return switch (this) {
            case Success<T>(T value) -> onSuccess.apply(value);
            case Error<T> error -> onError.apply(error);
        };
    }

    default WriteResult<T> peek(Consumer<T> consumer) {
        if (this instanceof Success<T>(T value)) {
            consumer.accept(value);
        }
        return this;
    }

    default T orElseThrow() {
        return switch (this) {
            case Success<T>(T value) -> value;
            case Error<T>(String message, Throwable cause) -> throw new IllegalStateException(message, cause);
        };
    }

    default T orElse(T defaultValue) {
        return switch (this) {
            case Success<T>(T value) -> value;
            case Error<T>(String message, Throwable cause) -> defaultValue;
        };
    }

    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    default boolean isError() {
        return this instanceof Error<T>;
    }
}
