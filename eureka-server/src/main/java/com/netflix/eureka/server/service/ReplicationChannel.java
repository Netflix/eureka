package com.netflix.eureka.server.service;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.ServiceChannel;
import rx.Observable;

/**
 * A {@link com.netflix.eureka.service.ServiceChannel} implementation representing a replication stream.
 *
 * @author Nitesh Kant
 */
public interface ReplicationChannel extends ServiceChannel {

    /**
     * Returns the replication stream. This will be a stream sent over the wire from the source server (owner of the
     * replication data) to the receiver of this replication data.
     *
     * @return The replication stream.
     */
    Observable<ChangeNotification<InstanceInfo>> asObservable();
}
