package com.netflix.eureka2.client.channel;

import rx.Observable;
import rx.functions.Action0;

/**
 * Provides the following:
 * - channelObservable that emits the latest active channel being used by the connection
 * - channelInputObservable for subscribing to data received by the channel from the remote endpoint
 * - retryableLifecycle that emits onError from the channel. Retries on this will retry on a new channel
 * - initObservable that onCompletes after the first success and only after the first success
 *
 * @author David Liu
 */
public class RetryableConnection<CHANNEL, INPUT> {
    private final Observable<CHANNEL> channelObservable;
    private final Observable<INPUT> channelInputObservable;
    private final Observable<Void> retryableLifecycle;
    private final Observable<Void> initObservable;
    private final Action0 shutdownHook;

    public RetryableConnection(Observable<CHANNEL> channelObservable,
                               Observable<INPUT> channelInputObservable,
                               Observable<Void> retryableLifecycle,
                               Observable<Void> initObservable,
                               Action0 shutdownHook) {
        this.channelObservable = channelObservable;
        this.channelInputObservable = channelInputObservable;
        this.retryableLifecycle = retryableLifecycle;
        this.initObservable = initObservable;
        this.shutdownHook = shutdownHook;
    }

    public Observable<CHANNEL> getChannelObservable() {
        return channelObservable;
    }

    public Observable<INPUT> getChannelInputObservable() {
        return channelInputObservable;
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
