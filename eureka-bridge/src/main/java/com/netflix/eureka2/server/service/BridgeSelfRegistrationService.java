package com.netflix.eureka2.server.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.Names;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.ServicePort;
import com.netflix.eureka2.server.config.InstanceInfoFromConfig;
import com.netflix.eureka2.server.registry.EurekaServerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Self registration service for Eureka Bridge servers
 * @author David Liu
 */
@Singleton
public class BridgeSelfRegistrationService implements SelfRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(BridgeSelfRegistrationService.class);

    private final BridgeServerConfig config;
    private final EurekaServerRegistry<InstanceInfo> eurekaRegistry;

    private final AtomicBoolean connected = new AtomicBoolean();
    private final ReplaySubject<InstanceInfo> replaySubject = ReplaySubject.create();

    @Inject
    public BridgeSelfRegistrationService(BridgeServerConfig config, EurekaServerRegistry eurekaRegistry) {
        this.config = config;
        this.eurekaRegistry = eurekaRegistry;
    }

    @PostConstruct
    public void addToLocalRegistry() {
        resolve().subscribe(new Action1<InstanceInfo>() {
            @Override
            public void call(InstanceInfo instanceInfo) {
                logger.info("Self registration with instance info {}", instanceInfo);
                eurekaRegistry.register(instanceInfo);
            }
        });
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        if (connected.compareAndSet(false, true)) {
            return connect();
        }
        return replaySubject;
    }

    public Observable<InstanceInfo> connect() {
        return new InstanceInfoFromConfig(config)
                .get()
                .map(new Func1<InstanceInfo.Builder, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(InstanceInfo.Builder builder) {
                        HashSet<ServicePort> ports = new HashSet<>();
                        ports.add(new ServicePort(Names.DISCOVERY, config.getDiscoveryPort(), false));

                        return builder.withPorts(ports).build();
                    }
                })
                .doOnEach(new Action1<Notification<? super InstanceInfo>>() {
                    @Override
                    public void call(Notification<? super InstanceInfo> notification) {
                        switch (notification.getKind()) {
                            case OnNext:
                                replaySubject.onNext((InstanceInfo) notification.getValue());
                                replaySubject.onCompleted();
                                logger.info("Own instance info resolved to {}", notification.getValue());
                                break;
                            case OnError:
                                replaySubject.onError(notification.getThrowable());
                                logger.error("Could not resolve own instance info", notification.getThrowable());
                                break;
                        }
                    }
                });
    }
}