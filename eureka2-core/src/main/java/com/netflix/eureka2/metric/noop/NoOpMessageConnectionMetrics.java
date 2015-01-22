package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.MessageConnectionMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpMessageConnectionMetrics implements MessageConnectionMetrics {

    public static final NoOpMessageConnectionMetrics INSTANCE = new NoOpMessageConnectionMetrics();

    @Override
    public void incrementConnectedClients() {
    }

    @Override
    public void decrementConnectedClients() {
    }

    @Override
    public void clientConnectionTime(long start) {
    }

    @Override
    public void incrementIncomingMessageCounter(Class<?> aClass, int amount) {
    }

    @Override
    public void incrementOutgoingMessageCounter(Class<?> aClass, int amount) {
    }
}
