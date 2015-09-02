package com.netflix.eureka2.server.service.bootstrap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotifications;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.resolver.EurekaClusterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
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
    public Observable<Void> loadIntoRegistry(final EurekaRegistry<InstanceInfo> registry, final Source source) {
        return bootstrapResolver.clusterTopologyChanges()
                .compose(ChangeNotifications.<ClusterAddress>buffers())
                .compose(ChangeNotifications.snapshots(CLUSTER_ADDRESS_IDENTITY))
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
    private Observable<Void> loadRegistryFromAnyAvailableServer(final List<ClusterAddress> clusterAddresses, final EurekaRegistry<InstanceInfo> registry, final Source source) {
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

    private Observable<Void> loadRegistryFromServer(final Server server, final EurekaRegistry<InstanceInfo> registry, final Source source) {
        logger.info("Bootstrapping registry from server {}...", server);

        final AtomicLong loaded = new AtomicLong();
        Observable<ChangeNotification<InstanceInfo>> notifications = createLightEurekaInterestClient(server)
                .forInterest(Interests.forFullRegistry())
                .doOnNext(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        if (notification.isDataNotification()) {
                            loaded.incrementAndGet();
                        }
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.error("Bootstrapping from server " + server + " failed", throwable);
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        logger.error("Bootstrapping from server " + server + " completed");
                    }
                })
                .materialize()
                .concatMap(new Func1<Notification<ChangeNotification<InstanceInfo>>, Observable<? extends ChangeNotification<InstanceInfo>>>() {
                    @Override
                    public Observable<? extends ChangeNotification<InstanceInfo>> call(Notification<ChangeNotification<InstanceInfo>> rxNotification) {
                        switch (rxNotification.getKind()) {
                            case OnNext:
                                return Observable.just(rxNotification.getValue());
                            case OnError:
                            case OnCompleted:
                            default: // should never get here
                        }

                        if (loaded.get() == 0) {
                            logger.info("Loaded 0 entries from peer; will retry on another server if available");
                            return Observable.error(new Exception("Loaded 0 entries from peer"));
                        } else {
                            logger.info("Loaded {} entries from {} peer; actual registry size at the moment is {}", loaded.get(), server, registry.size());
                            return Observable.empty();
                        }
                    }
                });

        return registry.connect(source, notifications);
    }

    /**
     * We override default implementation in test to inject mock.
     */
    protected LightEurekaInterestClient createLightEurekaInterestClient(Server server) {
        return new LightEurekaInterestClient(server, scheduler);
    }

    private static ChangeNotifications.Identity<ClusterAddress, String> CLUSTER_ADDRESS_IDENTITY =
            new ChangeNotifications.Identity<ClusterAddress, String>() {
                @Override
                public String getId(ClusterAddress data) {
                    return data.getHostName();
                }
            };
}
