package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.channel.InterestChannel.STATE;
import com.netflix.eureka2.metric.InterestChannelMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpInterestChannelMetrics implements InterestChannelMetrics {

    public static final NoOpInterestChannelMetrics INSTANCE = new NoOpInterestChannelMetrics();

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
