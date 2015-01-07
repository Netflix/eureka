package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.ServiceChannel;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * Interest channel for snapshot retrievals. Snapshots are one-time subscribe
 * operations, that result in channel termination as soon as the last snapshot
 * item is returned from a server.
 *
 * @author Tomasz Bak
 */
public interface SnapshotInterestChannel extends ServiceChannel {
    Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest);
}
