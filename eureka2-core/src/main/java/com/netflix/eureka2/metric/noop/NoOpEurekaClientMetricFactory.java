package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.InterestChannelMetrics;
import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;

/**
 * @author Tomasz Bak
 */
public class NoOpEurekaClientMetricFactory extends EurekaClientMetricFactory {

    @Override
    public MessageConnectionMetrics getRegistrationServerConnectionMetrics() {
        return NoOpMessageConnectionMetrics.INSTANCE;
    }

    @Override
    public MessageConnectionMetrics getDiscoveryServerConnectionMetrics() {
        return NoOpMessageConnectionMetrics.INSTANCE;
    }

    @Override
    public RegistrationChannelMetrics getRegistrationChannelMetrics() {
        return NoOpRegistrationChannelMetrics.INSTANCE;
    }

    @Override
    public InterestChannelMetrics getInterestChannelMetrics() {
        return NoOpInterestChannelMetrics.INSTANCE;
    }

    @Override
    public SerializedTaskInvokerMetrics getSerializedTaskInvokerMetrics(Class<?> serializedTaskInvokerClass) {
        return NoOpSerializedTaskInvokerMetrics.INSTANCE;
    }
}
