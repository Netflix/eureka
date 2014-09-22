package com.netflix.eureka.server.service;

import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.server.transport.ClientConnection;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
public class ReplicationChannelImpl extends AbstractChannel<ReplicationChannelImpl.STATES> implements ReplicationChannel {

    protected enum STATES {Idle}

    @SuppressWarnings("unused") private final InstanceInfo sourceServer;

    public ReplicationChannelImpl(InstanceInfo sourceServer, ClientConnection transport, EurekaRegistry registry) {
        super(STATES.Idle, transport, registry, 3, 30000);
        this.sourceServer = sourceServer;
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        return null;
    }

    @Override
    public Observable<Void> update(InstanceInfo newInfo) {
        return null;
    }

    @Override
    public Observable<Void> unregister(String instanceId) {
        return null;
    }
}
