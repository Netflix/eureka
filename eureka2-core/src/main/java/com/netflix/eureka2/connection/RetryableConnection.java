package com.netflix.eureka2.connection;

import rx.Observable;
import rx.functions.Action0;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides the following:
 * - channelObservable that emits the latest active channel being used by the connection
 * - channelInputObservable for subscribing to data received by the channel from the remote endpoint
 * - retryableLifecycle that emits onError from the channel. Retries on this will retry on a new channel
 * - initObservable that onCompletes after the first success and only after the first success
 *
 * @author David Liu
 */
public class RetryableConnection<CHANNEL> {
    private final Observable<CHANNEL> channelObservable;
    private final Observable<Void> retryableLifecycle;
    private final Observable<Void> initObservable;
    private final Action0 shutdownHook;

    private final AtomicBoolean closed;

    public RetryableConnection(Observable<CHANNEL> channelObservable,
                               Observable<Void> retryableLifecycle,
                               Observable<Void> initObservable,
                               Action0 shutdownHook) {
        this.channelObservable = channelObservable;
        this.retryableLifecycle = retryableLifecycle;
        this.initObservable = initObservable;
        this.shutdownHook = shutdownHook;
        this.closed = new AtomicBoolean(false);
    }

    public Observable<CHANNEL> getChannelObservable() {
        return channelObservable;
    }

    public Observable<Void> getRetryableLifecycle() {
        return retryableLifecycle;
    }

    public Observable<Void> getInitObservable() {
        return initObservable;
    }

    public void close() {
        boolean alreadyClosed = closed.getAndSet(true);
        if (!alreadyClosed) {
            shutdownHook.call();
        }
    }
}
