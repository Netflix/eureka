package com.netflix.eureka2.server;

import javax.inject.Provider;
import java.util.Arrays;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.server.config.EurekaServerConfig;
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

    public PeerAddressesProvider(EurekaServerConfig config, final ServiceType serviceType) {
        ResolverType resolverType = config.getServerResolverType();
        if (resolverType == null) {
            throw new IllegalArgumentException("Write cluster resolver type not defined");
        }
        addressStream = EurekaClusterResolvers.writeClusterResolverFromConfiguration(
                resolverType,
                Arrays.asList(config.getServerList()),
                Schedulers.computation()
        ).clusterTopologyChanges().map(new Func1<ChangeNotification<ClusterAddress>, ChangeNotification<Server>>() {
            @Override
            public ChangeNotification<Server> call(ChangeNotification<ClusterAddress> notification) {
                if (notification.getKind() == Kind.BufferSentinel) {
                    return ChangeNotification.bufferSentinel();
                }
                Server server = new Server(
                        notification.getData().getHostName(),
                        notification.getData().getPortFor(serviceType)
                );
                return new ChangeNotification<Server>(notification.getKind(), server);
            }
        });
    }

    public PeerAddressesProvider(Observable<ChangeNotification<Server>> addressStream) {
        this.addressStream = addressStream;
    }

    @Override
    public Observable<ChangeNotification<Server>> get() {
        return addressStream;
    }
}
