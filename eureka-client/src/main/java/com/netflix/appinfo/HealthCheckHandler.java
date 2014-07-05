package com.netflix.appinfo;

/**
 * This provides a more granular healthcheck contract than the existing {@link HealthCheckCallback}
 *
 * @author Nitesh Kant
 */
public interface HealthCheckHandler {

    InstanceInfo.InstanceStatus getStatus(InstanceInfo.InstanceStatus currentStatus);

}
