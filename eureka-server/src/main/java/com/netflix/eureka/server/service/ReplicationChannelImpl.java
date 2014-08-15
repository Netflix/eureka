package com.netflix.eureka.server.service;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
public class ReplicationChannelImpl extends AbstractChannel implements ReplicationChannel {

    private final InstanceInfo sourceServer;

    public ReplicationChannelImpl(InstanceInfo sourceServer) {
        this.sourceServer = sourceServer;
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> asObservable() {
        return Observable.error(new UnsupportedOperationException("Replication not implemented yet.")); // TODO: Replication
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        throw new RuntimeException("not implemented yet");
    }
}
