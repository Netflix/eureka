package com.netflix.eureka2.client;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.service.RegistrationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
@Singleton
public class RegistrationHandlerImpl implements RegistrationHandler {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationHandlerImpl.class);

    private final ClientChannelFactory channelFactory;
    private final ConcurrentHashMap<String, RegistrationChannel> instanceIdVsChannel;
    private volatile boolean shutdown;

    @Inject
    public RegistrationHandlerImpl(ClientChannelFactory channelFactory) {
        this.channelFactory = channelFactory;
        this.instanceIdVsChannel = new ConcurrentHashMap<>();
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        if (shutdown) {
            return Observable.error(new IllegalStateException("Registration handler is already shutdown."));
        }

        final RegistrationChannel newChannel = channelFactory.newRegistrationChannel();
        final RegistrationChannel existing = instanceIdVsChannel.putIfAbsent(instanceInfo.getId(), newChannel);
        if (null != existing) {
            return existing.update(instanceInfo); // Be more acceptable to failure in contract adherence from the user.
            // If it is the same instance as existing, the server should not
            // generate unnecessary notifications.
        }
        return newChannel.register(instanceInfo);
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        if (shutdown) {
            return Observable.error(new IllegalStateException("Registration handler is already shutdown."));
        }

        final RegistrationChannel registrationChannel = instanceIdVsChannel.remove(instanceInfo.getId());
        if (null == registrationChannel) {
            logger.info("Instance: %s is not registered. Ignoring unregister", instanceInfo);
            return Observable.empty(); // Be more acceptable to errors from user as unregister for non-existent instance is a no-op.
        }
        return registrationChannel.unregister();
    }

    @Override
    public Observable<Void> update(InstanceInfo instanceInfo) {
        if (shutdown) {
            return Observable.error(new IllegalStateException("Registration handler is already shutdown."));
        }

        final RegistrationChannel registrationChannel = instanceIdVsChannel.get(instanceInfo.getId());
        if (null == registrationChannel) {
            logger.info("Instance: %s is not registered. Relaying update as register.", instanceInfo);
            return register(instanceInfo); // Be more acceptable to errors from user.
        }
        return registrationChannel.update(instanceInfo);
    }

    @Override
    public void shutdown() {
        shutdown = true;
        channelFactory.shutdown();
        Set<Map.Entry<String, RegistrationChannel>> entries = instanceIdVsChannel.entrySet();
        for (Map.Entry<String, RegistrationChannel> entry : entries) {
            String instanceId = entry.getKey();
            logger.info("Shutting down registration handler. Unregister instance Id: " + instanceId);
            entry.getValue().unregister();
            entry.getValue().close();
        }
    }
}
