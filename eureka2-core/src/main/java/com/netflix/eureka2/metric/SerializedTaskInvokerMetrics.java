package com.netflix.eureka2.metric;

import java.util.concurrent.Callable;

/**
 * @author David Liu
 */
public interface SerializedTaskInvokerMetrics {

    void incrementInputSuccess();

    void incrementInputFailure();

    void incrementOutputSuccess();

    void incrementOutputFailure();

    void setQueueSizeMonitor(Callable<Long> n);
}
