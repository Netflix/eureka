package com.netflix.rx.eureka.server.service;

import com.google.inject.Inject;
import com.netflix.rx.eureka.Names;
import com.netflix.rx.eureka.registry.AddressSelector;
import com.netflix.rx.eureka.registry.DataCenterInfo;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.registry.ServicePort;
import com.netflix.rx.eureka.registry.datacenter.LocalDataCenterInfo;
import com.netflix.rx.eureka.server.BridgeServerConfig;
import com.netflix.rx.eureka.server.registry.EurekaServerRegistry;
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
        return resolveDataCenterInfo().take(1).map(new Func1<DataCenterInfo, InstanceInfo>() {
            @Override
            public InstanceInfo call(DataCenterInfo dataCenterInfo) {
                final String instanceId = config.getAppName() + '#' + System.currentTimeMillis();

                HashSet<ServicePort> ports = new HashSet<>();
                ports.add(new ServicePort(Names.REGISTRATION, config.getRegistrationPort(), false));
                ports.add(new ServicePort(Names.REPLICATION, config.getReplicationPort(), false));
                ports.add(new ServicePort(Names.DISCOVERY, config.getDiscoveryPort(), false));

                String address = AddressSelector.selectBy().publicIp(true).or().any().returnNameOrIp(dataCenterInfo.getAddresses());
                HashSet<String> healthCheckUrls = new HashSet<String>();
                healthCheckUrls.add("http://" + address + ':' + config.getWebAdminPort() + "/healthcheck");

                return new InstanceInfo.Builder()
                        .withId(instanceId)
                        .withApp(config.getAppName())
                        .withVipAddress(config.getVipAddress())
                        .withPorts(ports)
                        .withHealthCheckUrls(healthCheckUrls)
                        .withDataCenterInfo(dataCenterInfo)
                        .build();
            }
        }).doOnEach(new Action1<Notification<? super InstanceInfo>>() {
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

    private Observable<? extends DataCenterInfo> resolveDataCenterInfo() {
        return LocalDataCenterInfo.forDataCenterType(config.getDataCenterType());
    }
}