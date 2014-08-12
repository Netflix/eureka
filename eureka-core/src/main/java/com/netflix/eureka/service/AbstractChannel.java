package com.netflix.eureka.service;

/**
 * An abstract {@link ServiceChannel} implementation for common methods.
 *
 * @author Nitesh Kant
 */
public abstract class AbstractChannel implements ServiceChannel {

    @Override
    public void heartbeat() {
    }

    protected void onMissingHeartbeat() {
        close();
    }
}
