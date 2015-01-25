package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.MessageConnectionMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpMessageConnectionMetrics implements MessageConnectionMetrics {

    public static final NoOpMessageConnectionMetrics INSTANCE = new NoOpMessageConnectionMetrics();

    @Override
    public void incrementConnectionCounter() {
    }

    @Override
    public void decrementConnectionCounter() {
    }

    @Override
    public void connectionDuration(long start) {
    }

    @Override
    public void incrementIncomingMessageCounter(Class<?> aClass, int amount) {
    }

    @Override
    public void incrementOutgoingMessageCounter(Class<?> aClass, int amount) {
    }
}
