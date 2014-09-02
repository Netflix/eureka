package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.TransportClient;
import com.netflix.eureka.service.EurekaService;
import com.netflix.eureka.service.InterestChannel;
import com.netflix.eureka.service.RegistrationChannel;

/**
 * @author Nitesh Kant
 */
public class EurekaServiceImpl implements EurekaService {

    private final TransportClient readServerClient;
    private final TransportClient writeServerClient;

    protected EurekaServiceImpl(boolean readClient, TransportClient aClient) {
        if (readClient) {
            readServerClient = aClient;
            writeServerClient = null;
        } else {
            writeServerClient = aClient;
            readServerClient = null;
        }
    }


    protected EurekaServiceImpl(TransportClient writeServerClient, TransportClient readServerClient) {
        this.writeServerClient = writeServerClient;
        this.readServerClient = readServerClient;
    }

    public static EurekaService forReadServer(TransportClient client) {
        return new EurekaServiceImpl(true, client);
    }

    public static EurekaService forWriteServer(TransportClient client) {
        return new EurekaServiceImpl(false, client);
    }

    public static EurekaService forReadAndWriteServer(TransportClient readServerClient, TransportClient writeServerClient) {
        return new EurekaServiceImpl(writeServerClient, readServerClient);
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
        return new InterestChannelImpl(readServerClient);
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
    }
}
