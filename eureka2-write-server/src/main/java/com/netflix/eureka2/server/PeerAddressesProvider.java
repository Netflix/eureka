package com.netflix.eureka2.server;

import javax.inject.Provider;
import java.util.Arrays;

import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.resolver.ClusterAddress.ServiceType;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers.ResolverType;
import rx.Observable;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class PeerAddressesProvider implements Provider<Observable<ChangeNotification<Server>>> {

    private final Observable<ChangeNotification<Server>> addressStream;

    public PeerAddressesProvider(Observable<ChangeNotification<Server>> addressStream) {
        this.addressStream = addressStream;
    }

    public PeerAddressesProvider(EurekaClusterDiscoveryConfig config, final ServiceType serviceType) {
        this(addressStreamFromConfig(config, serviceType));
    }

    @Override
    public Observable<ChangeNotification<Server>> get() {
        return addressStream;
    }

    private static Observable<ChangeNotification<Server>> addressStreamFromConfig(EurekaClusterDiscoveryConfig config, final ServiceType serviceType) {
        Observable<ChangeNotification<Server>> addressStream;
        ResolverType resolverType = config.getClusterResolverType();
        if (resolverType == null) {
            throw new IllegalArgumentException("Write cluster resolver type not defined");
        }
        addressStream = EurekaClusterResolvers.writeClusterResolverFromConfiguration(
                resolverType,
                Arrays.asList(config.getClusterAddresses()),
                Schedulers.computation()
        ).clusterTopologyChanges().map(new Func1<ChangeNotification<ClusterAddress>, ChangeNotification<Server>>() {
            @Override
            public ChangeNotification<Server> call(ChangeNotification<ClusterAddress> notification) {
                if (notification.getKind() == Kind.BufferSentinel) {
                    return ChangeNotification.bufferSentinel();
                }
                Server server = new Server(
                        notification.getData().getHostName(),
                        notification.getData().getPortFor(ServiceType.Registration) // We run all on single port now
                );
                return new ChangeNotification<Server>(notification.getKind(), server);
            }
        });
        return addressStream;
    }
}
