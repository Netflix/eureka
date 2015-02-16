package com.netflix.eureka2.server;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.host.DnsChangeNotificationSource;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig.ServerBootstrap;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.Server;
import rx.Observable;
import rx.functions.Func1;

/**
 * Provider of peer write cluster nodes addresses.
 *
 * @author Tomasz Bak
 */
@Singleton
public class ReplicationPeerAddressesProvider implements Provider<Observable<ChangeNotification<Server>>> {

    private final EurekaServerConfig config;

    private Observable<ChangeNotification<Server>> addressStream;

    @Inject
    public ReplicationPeerAddressesProvider(EurekaServerConfig config) {
        this.config = config;
    }

    public ReplicationPeerAddressesProvider(Observable<ChangeNotification<Server>> addressStream) {
        this.config = null;
        this.addressStream = addressStream;
    }

    @PostConstruct
    public void createResolver() {
        if (config != null) {  // always postConstruct resolve if config exist
            EurekaCommonConfig.ResolverType resolverType = config.getServerResolverType();
            if (resolverType == null) {
                throw new IllegalArgumentException("Write cluster resolver type not defined");
            }

            EurekaCommonConfig.ServerBootstrap[] bootstraps = EurekaCommonConfig.ServerBootstrap.from(config.getServerList());
            switch (resolverType) {
                case dns:
                    addressStream = fromDns(bootstraps);
                    break;
                case fixed:
                    addressStream = fromList(bootstraps);
            }
        }
    }

    @Override
    public Observable<ChangeNotification<Server>> get() {
        return addressStream;
    }

    private static Observable<ChangeNotification<Server>> fromDns(ServerBootstrap[] bootstraps) {
        List<Observable<ChangeNotification<Server>>> addresses = new ArrayList<>(bootstraps.length);
        for (final ServerBootstrap sb : bootstraps) {
            Observable<ChangeNotification<Server>> stream = new DnsChangeNotificationSource(sb.getHostname())
                    .forInterest(null)
                    .map(new Func1<ChangeNotification<String>, ChangeNotification<Server>>() {
                        @Override
                        public ChangeNotification<Server> call(ChangeNotification<String> notification) {
                            return new ChangeNotification<>(
                                    notification.getKind(),
                                    new Server(notification.getData(), sb.getReplicationPort())
                            );
                        }
                    });
            addresses.add(stream);
        }
        return Observable.merge(addresses);
    }

    private static Observable<ChangeNotification<Server>> fromList(ServerBootstrap[] bootstraps) {
        List<ChangeNotification<Server>> addresses = new ArrayList<>(bootstraps.length);
        for (ServerBootstrap sb : bootstraps) {
            addresses.add(new ChangeNotification<>(
                            Kind.Add,
                            new Server(sb.getHostname(), sb.getReplicationPort()))
            );
        }
        return Observable.from(addresses);
    }
}
