package com.netflix.eureka2.server.resolver;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class StaticEurekaClusterResolver implements EurekaClusterResolver {

    private final Observable<ChangeNotification<ClusterAddress>> clusterObserver;

    public StaticEurekaClusterResolver(ClusterAddress clusterAddress) {
        this.clusterObserver = Observable.just(new ChangeNotification<ClusterAddress>(Kind.Add, clusterAddress));
    }

    public StaticEurekaClusterResolver(List<ClusterAddress> clusterAddresses) {
        List<ChangeNotification<ClusterAddress>> addNotifications = new ArrayList<>(clusterAddresses.size());
        for (ClusterAddress address : clusterAddresses) {
            addNotifications.add(new ChangeNotification<ClusterAddress>(Kind.Add, address));
        }
        addNotifications.add(ChangeNotification.<ClusterAddress>bufferSentinel());
        this.clusterObserver = Observable.from(addNotifications);
    }

    @Override
    public Observable<ChangeNotification<ClusterAddress>> clusterTopologyChanges() {
        return clusterObserver;
    }
}
