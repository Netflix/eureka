package com.netflix.eureka2.eureka1.rest.registry;

import java.util.Map;

import com.netflix.appinfo.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public interface Eureka1RegistryProxy {

    enum Result {Ok, NotFound, InvalidArguments}

    void register(InstanceInfo instanceInfo);

    Result appendMeta(String appName, String instanceId, Map<String, String> meta);

    Result unregister(String appName, String instanceId);

    /**
     * Used by {@link LeaseExpiryQueue} to cleanup expired entries.
     */
    void unregister(RegistrationHandler cleanupHandler, long expiryTime);

    Result renewLease(String appName, String instanceId);

    void shutdown();
}
