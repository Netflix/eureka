package com.netflix.eureka.registry;

/**
 * @author Nitesh Kant
 */
public class InstanceNotRegisteredException extends RuntimeException {

    public InstanceNotRegisteredException(String instanceId) {
        super("Instance with id: " + instanceId + " is not registered.");
    }
}
