package com.netflix.eureka2.server;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import rx.Observable;

/**
 * Provider of peer write cluster nodes addresses.
 *
 * @author Tomasz Bak
 */
@Singleton
public class ReplicationPeerAddressesProvider extends PeerAddressesProvider {

    @Inject
    public ReplicationPeerAddressesProvider(EurekaClusterDiscoveryConfig config) {
        super(config);
    }

    public ReplicationPeerAddressesProvider(Observable<ChangeNotification<Server>> addressStream) {
        super(addressStream);
    }
}
