package com.netflix.eureka2.server;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.server.resolver.EurekaEndpoint.ServiceType;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import rx.Observable;

/**
 * Provider of peer write cluster nodes addresses.
 *
 * @author Tomasz Bak
 */
@Singleton
public class ReplicationPeerAddressesProvider extends PeerAddressesProvider {

    @Inject
    public ReplicationPeerAddressesProvider(EurekaServerConfig config) {
        super(config, ServiceType.Replication);
    }

    public ReplicationPeerAddressesProvider(Observable<ChangeNotification<Server>> addressStream) {
        super(addressStream);
    }
}
