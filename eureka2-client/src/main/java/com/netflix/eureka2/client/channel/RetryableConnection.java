package com.netflix.eureka2.client.channel;

import rx.Observable;
import rx.functions.Action0;

/**
 * @author David Liu
 */
public class RetryableConnection<T> {
    private final Observable<T> channelObservable;
    private final Observable<Void> retryableLifecycle;
    private final Observable<Void> initObservable;
    private final Action0 shutdownHook;

    public RetryableConnection(Observable<T> channelObservable,
                               Observable<Void> retryableLifecycle,
                               Observable<Void> initObservable,
                               Action0 shutdownHook) {
        this.channelObservable = channelObservable;
        this.retryableLifecycle = retryableLifecycle;
        this.initObservable = initObservable;
        this.shutdownHook = shutdownHook;
    }

    public Observable<T> getChannelObservable() {
        return channelObservable;
    }

    public Observable<Void> getRetryableLifecycle() {
        return retryableLifecycle;
    }

    public Observable<Void> getInitObservable() {
        return initObservable;
    }

    public void close() {
        shutdownHook.call();
    }
}
