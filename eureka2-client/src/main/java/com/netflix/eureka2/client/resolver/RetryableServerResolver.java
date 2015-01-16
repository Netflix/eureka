package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import rx.Observable;

/**
 * A server resolver decorator with retry capabilities for resolve()
 *
 * @author David Liu
 */
public class RetryableServerResolver implements ServerResolver {

    private final ServerResolver delegate;
    private final RetryStrategyFunc retryStrategy;

    public RetryableServerResolver(ServerResolver delegate) {
        this(delegate, new RetryStrategyFunc(500, 5, true));
    }

    public RetryableServerResolver(ServerResolver delegate, RetryStrategyFunc retryStrategy) {
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
}
