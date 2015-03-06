package com.netflix.eureka2.eureka1.rest.registry;

import java.util.HashMap;
import java.util.Map;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka2.client.registration.EurekaRegistrationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;

/**
 * @author Tomasz Bak
 */
public class Eureka1RegistryProxyImpl implements Eureka1RegistryProxy {

    private static final Logger logger = LoggerFactory.getLogger(Eureka1RegistryProxyImpl.class);

    private final Map<String, RegistrationHandler> handlers = new HashMap<>();
    private final EurekaRegistrationClient registrationClient;
    private final Scheduler scheduler;

    private final LeaseExpiryQueue expiryQueue;

    public Eureka1RegistryProxyImpl(EurekaRegistrationClient registrationClient, Scheduler scheduler) {
        this.registrationClient = registrationClient;
        this.scheduler = scheduler;
        expiryQueue = new LeaseExpiryQueue(this, scheduler);
    }

    @Override
    public void register(InstanceInfo instanceInfo) {
        synchronized (handlers) {
            RegistrationHandler handler = handlers.get(instanceInfo.getId());
            if (handler == null) {
                handler = new RegistrationHandler(registrationClient, scheduler);
                handlers.put(instanceInfo.getId(), handler);
            }
            handler.register(instanceInfo);
            expiryQueue.enqueue(handler);
        }
    }

    @Override
    public Result appendMeta(String appName, String instanceId, Map<String, String> meta) {
        synchronized (handlers) {
            RegistrationHandler handler = handlers.get(instanceId);
            if (handler != null) {
                InstanceInfo v1InstanceInfo = handler.getV1InstanceInfo();
                if (appName.equalsIgnoreCase(v1InstanceInfo.getAppName())) {
                    InstanceInfo newInstanceInfo = merge(v1InstanceInfo, meta);
                    handler.register(newInstanceInfo);
                    return Result.Ok;
                }
                return Result.InvalidArguments;
            }
        }
        return Result.NotFound;
    }

    @Override
    public Result unregister(String appName, String instanceId) {
        synchronized (handlers) {
            RegistrationHandler handler = handlers.remove(instanceId);
            if (handler != null) {
                InstanceInfo v1InstanceInfo = handler.getV1InstanceInfo();
                if (appName.equalsIgnoreCase(v1InstanceInfo.getAppName())) {
                    handler.unregister();
                    return Result.Ok;
                }
                return Result.InvalidArguments;
            }
        }
        return Result.NotFound;
    }

    @Override
    public void unregister(RegistrationHandler cleanupHandler, long expiryTime) {
        synchronized (handlers) {
            String instanceId = cleanupHandler.getV1InstanceInfo().getId();
            RegistrationHandler activeHandler = handlers.get(instanceId);
            if (cleanupHandler == activeHandler && activeHandler.getExpiryTime() <= expiryTime) {
                activeHandler.unregister();
                handlers.remove(instanceId);
            }
        }
    }

    @Override
    public Result renewLease(String appName, String instanceId) {
        synchronized (handlers) {
            RegistrationHandler handler = handlers.get(instanceId);
            if (handler != null) {
                InstanceInfo v1InstanceInfo = handler.getV1InstanceInfo();
                if (appName.equalsIgnoreCase(v1InstanceInfo.getAppName())) {
                    handler.renew();
                    expiryQueue.enqueue(handler);
                    return Result.Ok;
                }
                logger.warn("Application name in the request, does not match the one in the instance info ({} != {}, instanceId={}",
                        appName, v1InstanceInfo.getAppName(), v1InstanceInfo.getId());
                return Result.InvalidArguments;
            }
        }
        return Result.NotFound;
    }

    @Override
    public void shutdown() {
        for (RegistrationHandler handler : handlers.values()) {
            handler.unregister();
        }
        handlers.clear();
    }

    private static InstanceInfo merge(InstanceInfo v1InstanceInfo, Map<String, String> meta) {
        Map<String, String> newMeta = new HashMap<>(v1InstanceInfo.getMetadata());
        newMeta.putAll(meta);
        InstanceInfo.Builder builder = new InstanceInfo.Builder(v1InstanceInfo);
        builder.setMetadata(newMeta);
        return builder.build();
    }
}
