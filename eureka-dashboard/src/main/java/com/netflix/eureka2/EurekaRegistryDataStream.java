package com.netflix.eureka2;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import rx.Observable;

@Singleton
public class EurekaRegistryDataStream {

    private final EurekaClient eurekaClient;

    @Inject
    public EurekaRegistryDataStream(EurekaDashboardConfig config) {
        ServerResolver resolver;
        final int discoveryPort = config.getWriteClusterDiscoveryPort();
        String[] writeClusterServers = config.getWriteClusterServers();
        switch (config.getResolverType()) {
            case "dns":
                String writeServerDnsName = writeClusterServers[0];
                resolver = ServerResolvers.forDnsName(writeServerDnsName, discoveryPort);
                break;
            case "inline":
                ServerResolver.Server[] servers = new ServerResolver.Server[writeClusterServers.length];
                for (int i = 0; i < writeClusterServers.length; i++) {
                    String serverHostName = writeClusterServers[i];
                    servers[i] = new ServerResolver.Server(serverHostName, discoveryPort);
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
