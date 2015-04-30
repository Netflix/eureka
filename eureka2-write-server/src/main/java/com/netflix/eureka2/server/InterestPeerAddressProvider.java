package com.netflix.eureka2.server;

import javax.inject.Inject;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.server.resolver.ClusterAddress.ServiceType;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class InterestPeerAddressProvider extends PeerAddressesProvider {
    @Inject
    public InterestPeerAddressProvider(EurekaServerConfig config) {
        super(config, ServiceType.Interest);
    }

    public InterestPeerAddressProvider(Observable<ChangeNotification<Server>> addressStream) {
        super(addressStream);
    }
}
