package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.InterestChannelMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpInterestChannelMetrics implements InterestChannelMetrics {

    public static final NoOpInterestChannelMetrics INSTANCE = new NoOpInterestChannelMetrics();
}
