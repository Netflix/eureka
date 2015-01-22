package com.netflix.eureka2.metric.noop;

import java.util.concurrent.Callable;

import com.netflix.eureka2.metric.client.EurekaClientRegistryMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpEurekaClientRegistryMetrics implements EurekaClientRegistryMetrics {

    public static final NoOpEurekaClientRegistryMetrics INSTANCE = new NoOpEurekaClientRegistryMetrics();

    @Override
    public void incrementRegistrationCounter() {
    }

    @Override
    public void incrementUnregistrationCounter() {
    }

    @Override
    public void incrementUpdateCounter() {
    }

    @Override
    public void setRegistrySizeMonitor(Callable<Integer> registrySizeCallable) {
    }
}
