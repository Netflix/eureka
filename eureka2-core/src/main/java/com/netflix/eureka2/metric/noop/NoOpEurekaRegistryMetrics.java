package com.netflix.eureka2.metric.noop;

import java.util.concurrent.Callable;

import com.netflix.eureka2.metric.EurekaRegistryMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpEurekaRegistryMetrics implements EurekaRegistryMetrics {

    public static final NoOpEurekaRegistryMetrics INSTANCE = new NoOpEurekaRegistryMetrics();

    @Override
    public void incrementRegistrationCounter(String origin) {
    }

    @Override
    public void incrementUnregistrationCounter(String origin) {
    }

    @Override
    public void incrementUpdateCounter(String origin) {
    }

    @Override
    public void setRegistrySizeMonitor(Callable<Integer> registrySizeFun) {
    }

    @Override
    public void setSelfPreservationMonitor(Callable<Integer> selfPreservationFun) {
    }
}
