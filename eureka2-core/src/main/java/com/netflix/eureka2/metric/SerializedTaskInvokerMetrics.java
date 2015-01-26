package com.netflix.eureka2.metric;

/**
 * @author David Liu
 */
public interface SerializedTaskInvokerMetrics {

    void incrementInputSuccess();

    void incrementInputFailure();

    void incrementOutputSuccess();

    void incrementOutputFailure();

    void setQueueSize(int queueSize);
}
