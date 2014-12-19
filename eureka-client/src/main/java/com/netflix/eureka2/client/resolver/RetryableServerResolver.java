package com.netflix.eureka2.client.resolver;

import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

import java.util.concurrent.TimeUnit;

/**
 * A server resolver decorator with retry capabilities for resolve()
 * @author David Liu
 */
public class RetryableServerResolver implements ServerResolver {

    private final ServerResolver delegate;
    private final RetryStrategy retryStrategy;

    public RetryableServerResolver(ServerResolver delegate) {
        this(delegate, new RetryStrategy(500, 5, true));
    }

    public RetryableServerResolver(ServerResolver delegate, RetryStrategy retryStrategy) {
        this.delegate = delegate;
        this.retryStrategy = retryStrategy;
    }

    @Override
    public Observable<Server> resolve() {
        return delegate.resolve().retryWhen(retryStrategy);
    }

    @Override
    public void close() {
        delegate.close();
    }


    public static class RetryStrategy implements Func1<Observable<? extends Throwable>, Observable<Long>> {
        private final long retryIntervalMillis;
        private final int numRetries;
        private final boolean backoffRetry;

        public RetryStrategy(long retryIntervalMillis, int numRetries, boolean backoffRetry) {
            this.retryIntervalMillis = retryIntervalMillis;
            this.numRetries = numRetries;
            this.backoffRetry = backoffRetry;
        }

        @Override
        public Observable<Long> call(Observable<? extends Throwable> observable) {
            return observable.zipWith(Observable.range(1, numRetries), new Func2<Throwable, Integer, Long>() {
                @Override
                public Long call(Throwable n, Integer i) {
                    if (backoffRetry) {
                        return i * retryIntervalMillis;
                    } else {
                        return retryIntervalMillis;
                    }
                }
            }).flatMap(new Func1<Long, Observable<Long>>() {
                @Override
                public Observable<Long> call(Long i) {
                    return Observable.timer(i, TimeUnit.MILLISECONDS);
                }
            });
        }
    }
}
