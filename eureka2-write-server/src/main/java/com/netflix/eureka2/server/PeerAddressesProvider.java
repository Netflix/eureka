package com.netflix.eureka2.server;

import javax.annotation.PostConstruct;
import javax.inject.Provider;
import java.util.Arrays;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.resolver.EurekaEndpoint;
import com.netflix.eureka2.server.resolver.EurekaEndpoint.ServiceType;
import com.netflix.eureka2.server.resolver.EurekaEndpointResolvers;
import com.netflix.eureka2.server.resolver.EurekaEndpointResolvers.ResolverType;
import rx.Observable;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class PeerAddressesProvider implements Provider<Observable<ChangeNotification<Server>>> {

    private final EurekaServerConfig config;
    private final ServiceType serviceType;

    private Observable<ChangeNotification<Server>> addressStream;

    public PeerAddressesProvider(EurekaServerConfig config, ServiceType serviceType) {
        this.config = config;
        this.serviceType = serviceType;
    }

    public PeerAddressesProvider(Observable<ChangeNotification<Server>> addressStream) {
        this.config = null;
        this.serviceType = null;
        this.addressStream = addressStream;
    }

    @PostConstruct
    public void createResolver() {
        if (config != null) {  // always postConstruct resolve if config exist
            ResolverType resolverType = config.getServerResolverType();
            if (resolverType == null) {
                throw new IllegalArgumentException("Write cluster resolver type not defined");
            }
            addressStream = EurekaEndpointResolvers.writeServerResolverFromConfiguration(
                    resolverType,
                    Arrays.asList(config.getServerList()),
                    Schedulers.computation()
            ).eurekaEndpoints().map(new Func1<ChangeNotification<EurekaEndpoint>, ChangeNotification<Server>>() {
                @Override
                public ChangeNotification<Server> call(ChangeNotification<EurekaEndpoint> notification) {
                    if (notification.getKind() == Kind.BufferSentinel) {
                        return ChangeNotification.bufferSentinel();
                    }
                    Server server = new Server(
                            notification.getData().getHostname(),
                            notification.getData().getPortFor(serviceType)
                    );
                    return new ChangeNotification<Server>(notification.getKind(), server);
                }
            });
        }
    }

    @Override
    public Observable<ChangeNotification<Server>> get() {
        return addressStream;
    }
}
