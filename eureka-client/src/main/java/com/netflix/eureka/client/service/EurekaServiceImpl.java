package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.TransportClient;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import com.netflix.eureka.service.RegistrationChannel;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
public class EurekaServiceImpl implements EurekaClientService {

    private final EurekaRegistry<InstanceInfo> registry;
    private final TransportClient readServerClient;
    private final TransportClient writeServerClient;

    protected EurekaServiceImpl(EurekaRegistry<InstanceInfo> registry, TransportClient writeServerClient, TransportClient readServerClient) {
        this.registry = registry;
        this.writeServerClient = writeServerClient;
        this.readServerClient = readServerClient;
    }

    public static EurekaClientService forReadServer(EurekaRegistry<InstanceInfo> registry, TransportClient client) {
        return new EurekaServiceImpl(registry, null, client);
    }

    public static EurekaClientService forWriteServer(EurekaRegistry<InstanceInfo> registry, TransportClient client) {
        return new EurekaServiceImpl(registry, client, null);
    }

    public static EurekaClientService forReadAndWriteServer(EurekaRegistry<InstanceInfo> registry, TransportClient readServerClient, TransportClient writeServerClient) {
        return new EurekaServiceImpl(registry, writeServerClient, readServerClient);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        return registry.forInterest(interest);
    }

    /**
     * Returns an {@link InterestChannel} which is not yet connected to any eureka servers. The connection is done
     * lazily when any operation is invoked on the channel.
     *
     * This makes it possible for clients to create this channel eagerly and use it when required.
     *
     * @return An {@link InterestChannel} which is not yet connected to any eureka servers.
     */
    @Override
    public InterestChannel newInterestChannel() {
        return new InterestChannelImpl(registry, readServerClient);
    }


    /**
     * Returns an {@link RegistrationChannel} which is not yet connected to any eureka servers. The connection is done
     * lazily when any operation is invoked on the channel.
     *
     * This makes it possible for clients to create this channel eagerly and use it when required.
     *
     * @return An {@link RegistrationChannel} which is not yet connected to any eureka servers.
     */
    @Override
    public RegistrationChannel newRegistrationChannel() {
        return new RegistrationChannelImpl(writeServerClient);
    }

    @Override
    public void shutdown() {
        if (null != readServerClient) {
            readServerClient.shutdown();
        }
        if (null != writeServerClient) {
            writeServerClient.shutdown();
        }
        if (null != registry) {
            registry.shutdown();
        }
    }

    // for debugging
    @Override
    public String toString() {
        return registry.toString();
    }
}
