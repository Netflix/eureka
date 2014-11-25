package com.netflix.eureka2;

import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import rx.Observable;
import com.google.inject.Singleton;
import com.google.inject.Inject;

@Singleton
public class EurekaRegistryDataStream {

    private final EurekaClient eurekaClient;
    private static final String EUREKA_WRITE_INSTANCE = "localhost";
    private static final int Eureka_DISCOVERY_PORT = 8080;

    @Inject
    public EurekaRegistryDataStream() {
        eurekaClient = Eureka.newClientBuilder(ServerResolvers.just(EUREKA_WRITE_INSTANCE, Eureka_DISCOVERY_PORT)).build();
    }

    public Observable<ChangeNotification<InstanceInfo>> getStream() {
        return eurekaClient.forInterest(Interests.forFullRegistry());
    }
}
