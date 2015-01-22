package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.channel.RegistrationChannel.STATE;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpRegistrationChannelMetrics implements RegistrationChannelMetrics {

    public static final NoOpRegistrationChannelMetrics INSTANCE = new NoOpRegistrationChannelMetrics();

    @Override
    public void incrementStateCounter(STATE state) {
    }

    @Override
    public void stateTransition(STATE from, STATE to) {
    }

    @Override
    public void decrementStateCounter(STATE state) {
    }
}
