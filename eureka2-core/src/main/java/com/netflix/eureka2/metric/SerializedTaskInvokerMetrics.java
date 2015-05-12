package com.netflix.eureka2.metric;

/**
 * @author David Liu
 */
public interface SerializedTaskInvokerMetrics {

    void incrementOutputSuccess();

    void incrementOutputFailure();
}
