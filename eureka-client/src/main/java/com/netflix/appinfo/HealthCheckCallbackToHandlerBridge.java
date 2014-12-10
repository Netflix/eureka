package com.netflix.appinfo;

/**
 * @author Nitesh Kant
 */
@SuppressWarnings("deprecation")
public class HealthCheckCallbackToHandlerBridge implements HealthCheckHandler {

    private final HealthCheckCallback callback;

    public HealthCheckCallbackToHandlerBridge() {
        callback = null;
    }

    public HealthCheckCallbackToHandlerBridge(HealthCheckCallback callback) {
        this.callback = callback;
    }

    @Override
    public InstanceInfo.InstanceStatus getStatus(InstanceInfo.InstanceStatus currentStatus) {
        return callback.isHealthy() ? InstanceInfo.InstanceStatus.UP : InstanceInfo.InstanceStatus.DOWN;
    }
}
