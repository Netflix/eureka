package com.netflix.eureka2.server;

import java.util.Arrays;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.resolver.OcelliServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.resolver.ClusterAddress.ServiceType;
import com.netflix.eureka2.server.resolver.EurekaClusterResolver;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers;
import rx.Observable;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class WriteClusterResolver extends OcelliServerResolver {

    private WriteClusterResolver(EurekaClusterResolver endpointResolver, final ServiceType serviceType) {
        super(toServerResolver(endpointResolver, serviceType));
    }

    private static Observable<ChangeNotification<Server>> toServerResolver(EurekaClusterResolver endpointResolver,
                                                                           final ServiceType serviceType) {
        return endpointResolver
                .clusterTopologyChanges()
                .map(new Func1<ChangeNotification<ClusterAddress>, ChangeNotification<Server>>() {
                    @Override
                    public ChangeNotification<Server> call(ChangeNotification<ClusterAddress> notification) {
                        if (notification.getKind() == Kind.BufferSentinel) {
                            return ChangeNotification.bufferSentinel();
                        }
                        return new ChangeNotification<Server>(
                                notification.getKind(),
                                new Server(
                                        notification.getData().getHostName(),
                                        notification.getData().getPortFor(serviceType)
                                )
                        );
                    }
                });
    }

    public static ServerResolver createRegistrationResolver(EurekaCommonConfig config) {
        return new WriteClusterResolver(createEurekaEndpointResolver(config), ServiceType.Registration);
    }

    public static ServerResolver createInterestResolver(EurekaCommonConfig config) {
        return new WriteClusterResolver(createEurekaEndpointResolver(config), ServiceType.Interest);
    }

    private static EurekaClusterResolver createEurekaEndpointResolver(EurekaCommonConfig config) {
        return EurekaClusterResolvers.writeClusterResolverFromConfiguration(config.getServerResolverType(), Arrays.asList(config.getServerList()), Schedulers.computation());
    }
}
