package com.netflix.eureka2.server.service.bootstrap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.LinkedHashSet;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotifications;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.resolver.EurekaEndpoint;
import com.netflix.eureka2.server.resolver.EurekaEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
@Singleton
public class PeerRegistryBootstrapService implements RegistryBootstrapService {

    private static final Logger logger = LoggerFactory.getLogger(PeerRegistryBootstrapService.class);

    private final EurekaEndpointResolver bootstrapResolver;
    private final Scheduler scheduler;

    @Inject
    public PeerRegistryBootstrapService(PeerBootstrapResolverProvider bootstrapResolverProvider) {
        this(bootstrapResolverProvider.get(), Schedulers.computation());
    }

    public PeerRegistryBootstrapService(EurekaEndpointResolver bootstrapResolver, Scheduler scheduler) {
        this.bootstrapResolver = bootstrapResolver;
        this.scheduler = scheduler;
    }

    @Override
    public Observable<Void> loadIntoRegistry(final SourcedEurekaRegistry<InstanceInfo> registry, final Source source) {
        return bootstrapResolver.eurekaEndpoints()
                .compose(ChangeNotifications.<EurekaEndpoint>buffers())
                .compose(ChangeNotifications.<EurekaEndpoint>snapshots())
                .take(1)
                .flatMap(new Func1<LinkedHashSet<EurekaEndpoint>, Observable<EurekaEndpoint>>() {
                    @Override
                    public Observable<EurekaEndpoint> call(LinkedHashSet<EurekaEndpoint> eurekaEndpoints) {
                        return Observable.from(eurekaEndpoints);
                    }
                }).flatMap(new Func1<EurekaEndpoint, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(EurekaEndpoint endpoint) {
                        return loadRegistryFrom(new Server(endpoint.getHostname(), endpoint.getInterestPort()), registry, source);
                    }
                })
                .filter(new Func1<Boolean, Boolean>() { // true - download succeeded, false - failed, so try another server
                    @Override
                    public Boolean call(Boolean status) {
                        return status;
                    }
                }).defaultIfEmpty(false).take(1).flatMap(new Func1<Boolean, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(Boolean status) {
                        if (status) {
                            logger.info("Bootstrap from peer completed successfully");
                            return Observable.empty();
                        }
                        logger.error("Cannot bootstrap registry from peers");
                        return Observable.error(new Exception("Cannot bootstrap registry from peers"));
                    }
                });
    }

    private Observable<Boolean> loadRegistryFrom(final Server server, final SourcedEurekaRegistry<InstanceInfo> registry, final Source source) {
        return createLightEurekaInterestClient(server).forInterest(Interests.forFullRegistry())
                .flatMap(new Func1<ChangeNotification<InstanceInfo>, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(ChangeNotification<InstanceInfo> notification) {
                        if (notification.isDataNotification()) {
                            Observable<Boolean> requestObserver;
                            switch (notification.getKind()) {
                                case Add:
                                case Modify:
                                    requestObserver = registry.register(notification.getData(), source);
                                    break;
                                default: // == Delete
                                    requestObserver = registry.unregister(notification.getData(), source);
                            }
                            return requestObserver.ignoreElements().cast(Void.class);
                        }
                        return Observable.empty();
                    }
                }).materialize().flatMap(new Func1<Notification<Void>, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(Notification<Void> notification) {
                        if (notification.getKind() == Notification.Kind.OnCompleted) {
                            return Observable.just(true);
                        }
                        // Materialize from Observablie<Void> so it must be Notification.Kind.OnError
                        logger.warn("Registry bootstrap from server {} failed", server);
                        return Observable.just(false);
                    }
                });
    }

    /**
     * We override default implementation in test to inject mock.
     */
    protected LightEurekaInterestClient createLightEurekaInterestClient(Server server) {
        return new LightEurekaInterestClient(server, scheduler);
    }
}
