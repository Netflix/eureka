package com.netflix.eureka2.server.service.bootstrap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.ChangeNotifications;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.resolver.EurekaClusterResolver;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
@Singleton
public class BackupClusterBootstrapService implements RegistryBootstrapService {

    private static final Logger logger = LoggerFactory.getLogger(BackupClusterBootstrapService.class);

    private final EurekaClusterResolver bootstrapResolver;
    private final Scheduler scheduler;

    @Inject
    public BackupClusterBootstrapService(BackupClusterResolverProvider bootstrapResolverProvider) {
        this(bootstrapResolverProvider.get(), Schedulers.computation());
    }

    public BackupClusterBootstrapService(EurekaClusterResolver bootstrapResolver, Scheduler scheduler) {
        this.bootstrapResolver = bootstrapResolver;
        this.scheduler = scheduler;
    }

    @Override
    public Observable<Void> loadIntoRegistry(final SourcedEurekaRegistry<InstanceInfo> registry, final Source source) {
        return bootstrapResolver.clusterTopologyChanges()
                .compose(ChangeNotifications.<ClusterAddress>buffers())
                .compose(ChangeNotifications.<ClusterAddress>snapshots())
                .take(1)
                .flatMap(new Func1<LinkedHashSet<ClusterAddress>, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(LinkedHashSet<ClusterAddress> clusterAddresses) {
                        if (clusterAddresses.isEmpty()) {
                            return Observable.error(new Exception("No peer server available"));
                        }
                        return loadRegistryFromAnyAvailableServer(new ArrayList<ClusterAddress>(clusterAddresses), registry, source);
                    }
                });
    }

    /**
     * This method is called recursively by retry logic.  The provided cluster address list will always hold at
     * least 1 item.
     */
    private Observable<Void> loadRegistryFromAnyAvailableServer(final List<ClusterAddress> clusterAddresses, final SourcedEurekaRegistry<InstanceInfo> registry, final Source source) {
        ClusterAddress firstEndpoint = clusterAddresses.get(0);
        Server firstServer = new Server(firstEndpoint.getHostName(), firstEndpoint.getInterestPort());

        return loadRegistryFromServer(firstServer, registry, source).onErrorResumeNext(new Func1<Throwable, Observable<Void>>() {
            @Override
            public Observable<Void> call(Throwable error) {
                if (clusterAddresses.size() <= 1) {
                    return Observable.error(new Exception("Could not bootstrap registry from any peer"));
                }
                return loadRegistryFromAnyAvailableServer(clusterAddresses.subList(1, clusterAddresses.size()), registry, source);
            }
        });
    }

    private Observable<Void> loadRegistryFromServer(final Server server, final SourcedEurekaRegistry<InstanceInfo> registry, final Source source) {
        logger.info("Bootstrapping registry from server {}...", server);
        return createLightEurekaInterestClient(server).forInterest(Interests.forFullRegistry())
                .flatMap(new Func1<ChangeNotification<InstanceInfo>, Observable<Integer>>() {
                    @Override
                    public Observable<Integer> call(final ChangeNotification<InstanceInfo> notification) {
                        if (notification.isDataNotification()) {
                            Observable<Boolean> requestObserver;
                            switch (notification.getKind()) {
                                case Add:
                                case Modify:
                                    requestObserver = registry.register(notification.getData(), source);
                                    break;
                                case Delete:
                                    requestObserver = registry.unregister(notification.getData(), source);
                                    break;
                                default:
                                    throw new IllegalStateException("Unexpected enum value " + notification.getKind());
                            }
                            return requestObserver
                                    .ignoreElements()
                                    .cast(Void.class)
                                    .materialize()
                                    .map(new Func1<Notification<Void>, Integer>() {
                                        @Override
                                        public Integer call(Notification<Void> modifyResult) {
                                            switch (modifyResult.getKind()) {
                                                case OnCompleted:
                                                    return notification.getKind() == Kind.Delete ? -1 : 1;
                                                case OnError:
                                                    return 0;
                                            }
                                            return 0;
                                        }
                                    });
                        }
                        return Observable.empty();
                    }
                }).reduce(0, new Func2<Integer, Integer, Integer>() {
                    @Override
                    public Integer call(Integer accumulator, Integer change) {
                        return accumulator + change;
                    }
                }).doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable error) {
                        logger.error("Bootstrapping from server " + server + " failed", error);
                    }
                }).flatMap(new Func1<Integer, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(Integer loaded) {
                        if (loaded == 0) {
                            logger.info("Loaded 0 entries from peer; will retry on another server if available");
                            return Observable.error(new Exception("Loaded 0 entries from peer"));
                        }
                        logger.info("Loaded {} entries from {} peer; actual registry size at the moment is {}", loaded, server, registry.size());
                        return Observable.empty();
                    }
                });
    }

    /**
     * We override default implementation in test to inject mock.
     */
    protected LightEurekaInterestClient createLightEurekaInterestClient(Server server) {
        return new LightEurekaInterestClient(server, scheduler);
    }

    public static void main(String[] args) {
        EurekaClusterResolver resolver = EurekaClusterResolvers.writeClusterResolverFromConfiguration(
                "ec2-50-19-255-84.compute-1.amazonaws.com:12102:12103:12104", true, Schedulers.computation()
//                "txt.us-east-1.eureka2dev.discoverytest.netflix.net:12102:12103:12104", true, Schedulers.computation()
        );
        BackupClusterBootstrapService bootstrapService = new BackupClusterBootstrapService(resolver, Schedulers.computation());
        SourcedEurekaRegistryImpl registry = new SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics());
        Source source = new Source(Origin.BOOTSTRAP, "test");
        bootstrapService.loadIntoRegistry(registry, source).toBlocking().firstOrDefault(null);
    }
}
