package io.github.auspis.fluentrepo4j.functional.read;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface ReadResult<T> {

    record Found<T>(T value) implements ReadResult<T> {
        public Found {
            if (value == null) {
                throw new IllegalArgumentException("Found value must not be null");
            }
        }
    }

    record NotFound<T>() implements ReadResult<T> {}

    record Error<T>(String message, Throwable cause) implements ReadResult<T> {
        public Error {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("Error message must not be null or blank");
            }
        }

        public Error(String message) {
            this(message, null);
        }
    }

    default <U> ReadResult<U> map(Function<T, U> mapper) {
        return switch (this) {
            case Found<T>(T value) -> new Found<>(mapper.apply(value));
            case NotFound<T> ignored -> new NotFound<>();
            case Error<T>(String message, Throwable cause) -> new Error<>(message, cause);
        };
    }

    default <U> U fold(Function<T, U> onFound, Supplier<U> onNotFound, Function<Error<T>, U> onError) {
        return switch (this) {
            case Found<T>(T value) -> onFound.apply(value);
            case NotFound<T> ignored -> onNotFound.get();
            case Error<T> error -> onError.apply(error);
        };
    }

    default ReadResult<T> peek(Consumer<T> consumer) {
        if (this instanceof Found<T>(T value)) {
            consumer.accept(value);
        }
        return this;
    }

    default T orElse(T defaultValue) {
        return switch (this) {
            case Found<T>(T value) -> value;
            case NotFound<T> ignored -> defaultValue;
            case Error<T>(String message, Throwable cause) -> defaultValue;
        };
    }

    default T orElseGet(Supplier<T> supplier) {
        return switch (this) {
            case Found<T>(T value) -> value;
            case NotFound<T> ignored -> supplier.get();
            case Error<T>(String message, Throwable cause) -> supplier.get();
        };
    }

    default T orElseThrow() {
        return switch (this) {
            case Found<T>(T value) -> value;
            case NotFound<T> ignored -> throw new NoSuchElementException("No result found");
            case Error<T>(String message, Throwable cause) -> throw new IllegalStateException(message, cause);
        };
    }

    default boolean isFound() {
        return this instanceof Found<T>;
    }

    default boolean isNotFound() {
        return this instanceof NotFound<T>;
    }

    default boolean isError() {
        return this instanceof Error<T>;
    }
}
