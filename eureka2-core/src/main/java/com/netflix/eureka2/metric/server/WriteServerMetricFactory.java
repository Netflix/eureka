package com.netflix.eureka2.metric.server;

import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.metric.noop.NoOpWriteServerMetricFactory;

/**
 * @author Tomasz Bak
 */
public abstract class WriteServerMetricFactory extends EurekaServerMetricFactory {

    private static volatile WriteServerMetricFactory defaultFactory = new NoOpWriteServerMetricFactory();

    public abstract MessageConnectionMetrics getReplicationSenderConnectionMetrics();

    public abstract MessageConnectionMetrics getReplicationReceiverConnectionMetrics();

    public abstract RegistrationChannelMetrics getRegistrationChannelMetrics();

    public abstract ReplicationChannelMetrics getReplicationChannelMetrics();

    public static WriteServerMetricFactory writeServerMetrics() {
        return defaultFactory;
    }

    public static void setDefaultWriteMetricFactory(WriteServerMetricFactory newFactory) {
        defaultFactory = newFactory;
    }
}
