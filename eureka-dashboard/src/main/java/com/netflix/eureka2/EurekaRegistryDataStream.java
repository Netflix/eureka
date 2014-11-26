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
import com.netflix.eureka2.server.config.EurekaBootstrapConfig;
import rx.Observable;

@Singleton
public class EurekaRegistryDataStream {

    private final EurekaClient eurekaClient;

    @Inject
    public EurekaRegistryDataStream(EurekaDashboardConfig config) {
        ServerResolver resolver;
        switch (config.getResolverType()) {
            case "dns":
                EurekaBootstrapConfig.WriteServerBootstrap server = config.getWriteClusterServerDns();
                resolver = ServerResolvers.forDnsName(server.getHostname(), server.getDiscoveryPort());
                break;
            case "inline":
                EurekaBootstrapConfig.WriteServerBootstrap[] serverBootstraps = config.getWriteClusterServersInline();
                ServerResolver.Server[] servers = new ServerResolver.Server[serverBootstraps.length];
                for (int i = 0; i < servers.length; i++) {
                    servers[i] = new ServerResolver.Server(
                            serverBootstraps[i].getHostname(),
                            serverBootstraps[i].getDiscoveryPort()
                    );
                }
                resolver = ServerResolvers.from(servers);
                break;
            default:
                throw new IllegalArgumentException("Unrecognized write cluster resolver");
        }

        eurekaClient = Eureka.newClientBuilder(resolver).build();
    }

    public Observable<ChangeNotification<InstanceInfo>> getStream() {
        return eurekaClient.forInterest(Interests.forFullRegistry());
    }
}
