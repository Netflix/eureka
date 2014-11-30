package com.netflix.eureka2;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig.ServerBootstrap;
import rx.Observable;
import rx.Subscriber;

@Singleton
public class EurekaRegistryDataStream {

    private final EurekaClient eurekaClient;

    @Inject
    public EurekaRegistryDataStream(EurekaDashboardConfig config) {
        ServerResolver resolver;

        EurekaCommonConfig.ResolverType resolverType = config.getServerResolverType();
        ServerBootstrap[] bootstraps = ServerBootstrap.from(config.getServerList());
        ServerResolver[] resolvers = new ServerResolver[bootstraps.length];

        for (int i = 0; i < resolvers.length; i++) {
            resolvers[i] = resolveForType(bootstraps[i], resolverType);
        }

        resolver = ServerResolvers.from(resolvers);

        eurekaClient = Eureka.newClientBuilder(resolver).build();
    }

    public void subscribe(Subscriber<ChangeNotification<InstanceInfo>> subscriber) {
        getStream().subscribe(subscriber);
    }

    private ServerResolver resolveForType(ServerBootstrap bootstrap, EurekaCommonConfig.ResolverType resolverType) {
        if (resolverType == null) {
            throw new IllegalArgumentException("Write cluster resolver type not defined");
        }

        ServerResolver resolver;
        switch (resolverType) {
            case dns:
                resolver = ServerResolvers.forDnsName(bootstrap.getHostname(), bootstrap.getDiscoveryPort());
                break;
            case fixed:
                resolver = ServerResolvers.just(bootstrap.getHostname(), bootstrap.getDiscoveryPort());
                break;
            default:
                throw new IllegalArgumentException("Unrecognized write cluster resolver");
        }
        return resolver;
    }

    private Observable<ChangeNotification<InstanceInfo>> getStream() {
        return eurekaClient.forInterest(Interests.forFullRegistry());
    }

}
