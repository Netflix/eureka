package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.RegistrationChannelMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpRegistrationChannelMetrics implements RegistrationChannelMetrics {

    public static final NoOpRegistrationChannelMetrics INSTANCE = new NoOpRegistrationChannelMetrics();
}
