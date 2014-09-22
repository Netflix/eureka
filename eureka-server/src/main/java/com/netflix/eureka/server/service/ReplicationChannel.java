package com.netflix.eureka.server.service;

import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.Lease;
import com.netflix.eureka.service.ServiceChannel;
import rx.Observable;

/**
 * A {@link com.netflix.eureka.service.ServiceChannel} implementation representing a replication stream
 * between two Eureka write servers.
 *
 * The client side of the channel is a source of data, that comes from its own registry, and is limited
 * to entries with {@link Lease.Origin#ATTACHED_CLIENT}.
 *
 * On the server side the data are put into the registry with origin set to {@link Lease.Origin#REPLICATED}.
 * A replicated entry is removed from the registry under following circumstances:
 * <ul>
 *     <li>Explicite {@link #unregister(String)} call - an entry was removed from the source registry</li>
 *     <li>Replication connection termination - invalidates all entries replicated over this connection</li>
 *     <li>No heartbeat within configured period of time - equivalent to connection termination</li>
 * </ul>
 *
 * @author Nitesh Kant
 */
public interface ReplicationChannel extends ServiceChannel {

    /**
     * Registers the passed instance with eureka.
     *
     * @param instanceInfo The instance definition.
     *
     * @return Acknowledgment for the registration.
     */
    Observable<Void> register(InstanceInfo instanceInfo);

    /**
     * Updates the {@link InstanceInfo} registered with this channel.
     *
     * @param newInfo The updated info.
     *
     * @return Acknowledgment for this update.
     */
    Observable<Void> update(InstanceInfo newInfo);

    /**
     * Unregisters the {@link InstanceInfo} with given id.
     *
     * @return Acknowledgment for unregistration.
     */
    Observable<Void> unregister(String instanceId);
}
