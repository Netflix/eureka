package com.netflix.eureka.test.async.executor;

import java.util.concurrent.Future;

/**
 * Async result which extends {@link Future}.
 *
 * @param <T> The result type.
 */
public interface AsyncResult<T> extends Future<T> {

    /**
     * Handle result normally.
     *
     * @param result result.
     */
    void handleResult(T result);

    /**
     * Handle error.
     *
     * @param error error during execution.
     */
    void handleError(Throwable error);

    /**
     * Get result which will be blocked until the result is available or an error occurs.
     */
    T getResult() throws AsyncExecutorException;

    /**
     * Get error if possible.
     *
     * @return error.
     */
    Throwable getError();
}
