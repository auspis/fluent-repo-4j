package io.github.auspis.fluentrepo4j.functional;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents the result of a repository operation that can succeed or fail.
 *
 * <p>This sealed interface provides a type-safe way to handle success and failure cases
 * without relying on unchecked exceptions for expected outcomes such as optimistic
 * locking failures. Infrastructure errors (e.g. connection failures) are <b>not</b>
 * captured here and continue to propagate as Spring's {@code DataAccessException}.
 *
 * <p>Use {@link Success} when the operation completes normally, and {@link Failure}
 * when a domain-level or validation error occurs.
 *
 * <h3>Example usage</h3>
 * <pre>{@code
 * RepositoryResult<User> result = userRepository.save(user);
 *
 * result.fold(
 *     saved -> System.out.println("Saved: " + saved.getId()),
 *     failure -> System.err.println("Failed: " + failure.message())
 * );
 * }</pre>
 *
 * @param <T> the type of the successful result
 */
public sealed interface RepositoryResult<T> {

    /**
     * Successful outcome carrying the result value.
     *
     * @param value the result value, must not be {@code null}
     * @param <T>   the result type
     */
    record Success<T>(T value) implements RepositoryResult<T> {
        public Success {
            if (value == null) {
                throw new IllegalArgumentException("Success value must not be null");
            }
        }
    }

    /**
     * Failed outcome carrying a structured error description.
     *
     * @param message a human-readable description of the failure
     * @param cause   the underlying exception, or {@code null} if none
     * @param <T>     the result type (phantom — no value is present)
     */
    record Failure<T>(String message, Throwable cause) implements RepositoryResult<T> {
        public Failure {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("Failure message must not be null or blank");
            }
        }

        /**
         * Creates a {@link Failure} without an underlying cause.
         */
        public Failure(String message) {
            this(message, null);
        }
    }

    // ---- Combinators ----

    /**
     * Transforms the success value using the given mapping function.
     * If this is a {@link Failure}, the failure is propagated unchanged.
     *
     * @param mapper the function to apply to the success value
     * @param <U>    the new success type
     * @return a new {@link RepositoryResult} with the mapped value or the original failure
     */
    default <U> RepositoryResult<U> map(Function<T, U> mapper) {
        return switch (this) {
            case Success<T>(T value) -> new Success<>(mapper.apply(value));
            case Failure<T>(String message, Throwable cause) -> new Failure<>(message, cause);
        };
    }

    /**
     * Transforms this result into a single value by applying one of two functions.
     *
     * <p>Forces explicit handling of both success and failure cases.
     *
     * @param onSuccess the function to apply on success
     * @param onFailure the function to apply on failure
     * @param <U>       the unified return type
     * @return the result of applying the appropriate function
     */
    default <U> U fold(Function<T, U> onSuccess, Function<Failure<T>, U> onFailure) {
        return switch (this) {
            case Success<T>(T value) -> onSuccess.apply(value);
            case Failure<T> f -> onFailure.apply(f);
        };
    }

    /**
     * Executes a side-effect on the success value without transforming the result.
     *
     * @param consumer the action to perform on the success value
     * @return this result unchanged
     */
    default RepositoryResult<T> peek(Consumer<T> consumer) {
        if (this instanceof Success<T>(T value)) {
            consumer.accept(value);
        }
        return this;
    }

    /**
     * Returns the success value or throws an {@link IllegalStateException}
     * wrapping the failure cause.
     *
     * @return the success value
     * @throws IllegalStateException if this is a {@link Failure}
     */
    default T orElseThrow() {
        return switch (this) {
            case Success<T>(T value) -> value;
            case Failure<T>(String message, Throwable cause) -> throw new IllegalStateException(message, cause);
        };
    }

    /**
     * Returns the success value or the given default if this is a {@link Failure}.
     *
     * @param defaultValue the fallback value
     * @return the success value or the default
     */
    default T orElse(T defaultValue) {
        return switch (this) {
            case Success<T>(T value) -> value;
            case Failure<T>(String message, Throwable cause) -> defaultValue;
        };
    }

    /**
     * Returns {@code true} if this is a {@link Success}.
     */
    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    /**
     * Returns {@code true} if this is a {@link Failure}.
     */
    default boolean isFailure() {
        return this instanceof Failure<T>;
    }
}
