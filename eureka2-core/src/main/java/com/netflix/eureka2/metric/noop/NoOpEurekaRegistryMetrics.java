package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.EurekaRegistryMetrics;
import com.netflix.eureka2.model.Source.Origin;

/**
 * @author Tomasz Bak
 */
public class NoOpEurekaRegistryMetrics implements EurekaRegistryMetrics {

    public static final NoOpEurekaRegistryMetrics INSTANCE = new NoOpEurekaRegistryMetrics();

    @Override
    public void incrementRegistrationCounter(Origin origin) {
    }

    @Override
    public void incrementUnregistrationCounter(Origin origin) {
    }

    @Override
    public void setRegistrySize(int registrySize) {
    }

    @Override
    public void setSelfPreservation(boolean status) {
    }
}
