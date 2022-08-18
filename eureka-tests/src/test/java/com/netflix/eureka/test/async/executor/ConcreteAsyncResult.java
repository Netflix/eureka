package com.netflix.eureka.test.async.executor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Async result which is usually hold be caller to retrieve result or error.
 */
public class ConcreteAsyncResult<T> implements AsyncResult<T> {

    private final CountDownLatch latch = new CountDownLatch(1);

    private T result = null;

    private Throwable error = null;

    public ConcreteAsyncResult() {

    }

    /**
     * Sets the result and unblocks all threads waiting on {@link #get()} or {@link #get(long, TimeUnit)}.
     *
     * @param result the result to set.
     */
    @Override
    public void handleResult(T result) {
        this.result = result;
        latch.countDown();
    }

    /**
     * Sets an error thrown during execution, and unblocks all threads waiting on {@link #get()} or
     * {@link #get(long, TimeUnit)}.
     *
     * @param error the RPC error to set.
     */
    public void handleError(Throwable error) {
        this.error = error;
        latch.countDown();
    }

    /**
     * Gets the value of the result in blocking way. Using {@link #get()} or {@link #get(long, TimeUnit)} is
     * usually preferred because these methods block until the result is available or an error occurs.
     *
     * @return the value of the response, or null if no result was returned or the RPC has not yet completed.
     */
    public T getResult() throws AsyncExecutorException {
        return get();
    }

    /**
     * Gets the error that was thrown during execution. Does not block. Either {@link #get()} or
     * {@link #get(long, TimeUnit)} should be called first because these methods block until execution has completed.
     *
     * @return the error that was thrown, or null if no error has occurred or if the execution has not yet completed.
     */
    public Throwable getError() {
        return error;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        latch.countDown();
        return false;
    }

    public boolean isCancelled() {
        return false;
    }

    public T get() throws AsyncExecutorException {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new AsyncExecutorException("Interrupted", error);
        }
        if (error != null) {
            if (error instanceof AsyncExecutorException) {
                throw (AsyncExecutorException) error;
            } else {
                throw new AsyncExecutorException("Execution exception", error);
            }
        }
        return result;
    }

    public T get(long timeout, TimeUnit unit) throws AsyncExecutorException {
        try {
            if (latch.await(timeout, unit)) {
                if (error != null) {
                    if (error instanceof AsyncExecutorException) {
                        throw (AsyncExecutorException) error;
                    } else {
                        throw new RuntimeException("call future get exception", error);
                    }
                }
                return result;
            } else {
                throw new AsyncExecutorException("async get time out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AsyncExecutorException("call future is interuptted", e);
        }
    }

    /**
     * Waits for the CallFuture to complete without returning the result.
     *
     * @throws InterruptedException if interrupted.
     */
    public void await() throws InterruptedException {
        latch.await();
    }

    /**
     * Waits for the CallFuture to complete without returning the result.
     *
     * @param timeout the maximum time to wait.
     * @param unit    the time unit of the timeout argument.
     * @throws InterruptedException if interrupted.
     * @throws TimeoutException     if the wait timed out.
     */
    public void await(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        if (!latch.await(timeout, unit)) {
            throw new TimeoutException();
        }
    }

    public boolean isDone() {
        return latch.getCount() <= 0;
    }
}
