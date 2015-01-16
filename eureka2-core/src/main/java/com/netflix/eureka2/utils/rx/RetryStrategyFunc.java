package com.netflix.eureka2.utils.rx;

import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

import java.util.concurrent.TimeUnit;

/**
 * A Func1 with retry options for use in .retryWhen(new RetryStrategyFunc())
 *
 * @author David Liu
 */
public class RetryStrategyFunc implements Func1<Observable<? extends Throwable>, Observable<Long>> {
    private final long retryIntervalMillis;
    private final int numRetries;
    private final boolean backoffRetry;

    /**
     * @param retryIntervalMillis the initial wait between retries in milliseconds
     * @param totalRetries max number of retries to attempt
     * @param exponentialBackoff boolean to denote whether to use exponential backoff
     */
    public RetryStrategyFunc(long retryIntervalMillis, int totalRetries, boolean exponentialBackoff) {
        this.retryIntervalMillis = retryIntervalMillis;
        this.numRetries = totalRetries;
        this.backoffRetry = exponentialBackoff;
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
