package com.netflix.eureka2.eureka1.bridge;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka2.eureka1.bridge.config.Eureka1BridgeConfiguration;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.metric.server.BridgeChannelMetrics;
import com.netflix.eureka2.metric.server.BridgeServerMetricFactory;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Source.Origin;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.model.notification.SourcedChangeNotification;
import com.netflix.eureka2.model.notification.SourcedStreamStateNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification.BufferState;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.utils.rx.LoggingSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka2xInstanceInfo;

/**
 */
@Singleton
public class Eureka1Bridge {

    private static final Logger logger = LoggerFactory.getLogger(Eureka1Bridge.class);

    private static final String SOURCE_NAME = "eureka1Bridge";

    private final SourcedStreamStateNotification<InstanceInfo> bufferStart;
    private final SourcedStreamStateNotification<InstanceInfo> bufferEnd;

    private final EurekaRegistry<InstanceInfo> registry;
    private final DiscoveryClient discoveryClient;
    private final Eureka1BridgeConfiguration config;
    private final BridgeChannelMetrics metrics;
    private final Source selfSource;
    private final Worker worker;

    private final PublishSubject<ChangeNotification<InstanceInfo>> bridgeSubject;
    private final Subscriber<Void> bridgeSubscriber;

    public Eureka1Bridge(EurekaRegistry<InstanceInfo> registry,
                         DiscoveryClient discoveryClient,
                         Eureka1BridgeConfiguration config,
                         BridgeServerMetricFactory metricsFactory,
                         Scheduler scheduler) {
        this.registry = registry;
        this.discoveryClient = discoveryClient;
        this.config = config;
        this.metrics = metricsFactory.getBridgeChannelMetrics();
        this.selfSource = InstanceModel.getDefaultModel().createSource(Origin.REPLICATED, SOURCE_NAME);
        this.worker = scheduler.createWorker();

        this.bridgeSubscriber = new LoggingSubscriber<>(logger);
        this.bridgeSubject = PublishSubject.create();

        bufferStart = new SourcedStreamStateNotification<>(BufferState.BufferStart, Interests.forFullRegistry(), selfSource);
        bufferEnd = new SourcedStreamStateNotification<>(BufferState.BufferEnd, Interests.forFullRegistry(), selfSource);
    }

    @Inject
    public Eureka1Bridge(EurekaRegistry registry,
                         DiscoveryClient discoveryClient,
                         Eureka1BridgeConfiguration config,
                         BridgeServerMetricFactory metricsFactory) {
        this(registry, discoveryClient, config, metricsFactory, Schedulers.io());
    }

    @PostConstruct
    public void start() {
        registry.connect(selfSource, bridgeSubject).subscribe(bridgeSubscriber);

        worker.schedulePeriodically(
                new Action0() {
                    @Override
                    public void call() {
                        loadUpdatesFromV1Registry();
                    }
                }, 0, config.getRefreshRateSec(), TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        if (!bridgeSubscriber.isUnsubscribed()) {
            bridgeSubscriber.unsubscribe();
        }
        if (!worker.isUnsubscribed()) {
            worker.unsubscribe();
        }
    }

    private void loadUpdatesFromV1Registry() {
        logger.info("Starting new round of replication from v1 to v2");
        try {
            diff();
        } catch (Exception e) {
            logger.error("Eureka1 registry synchronization failure", e);
        }
    }

    private void diff() {
        final AtomicInteger totalCount = new AtomicInteger(0);
        final AtomicInteger updateCount = new AtomicInteger(0);
        final AtomicInteger registerCount = new AtomicInteger(0);
        final AtomicInteger unregisterCount = new AtomicInteger(0);

        final Map<String, InstanceInfo> newSnapshot = new HashMap<>();

        List<InstanceInfo> latestV1Instances = getNewEureka1Snapshot();
        Map<String, InstanceInfo> currentEureka1Snapshot = getCurrentEureka1Snapshot();

        bridgeSubject.onNext(bufferStart);
        for (InstanceInfo instanceInfo : latestV1Instances) {
            totalCount.incrementAndGet();
            newSnapshot.put(instanceInfo.getId(), instanceInfo);
            if (currentEureka1Snapshot.containsKey(instanceInfo.getId())) {
                InstanceInfo older = currentEureka1Snapshot.get(instanceInfo.getId());
                if (!older.equals(instanceInfo)) {
                    logger.info("Updating IInstanceInfo {} data from Eureka1 registry", instanceInfo.getId());
                    bridgeSubject.onNext(new SourcedChangeNotification<>(Kind.Modify, instanceInfo, selfSource));
                    updateCount.incrementAndGet();
                }
            } else {
                logger.info("Registering new IInstanceInfo {} data from Eureka1 registry", instanceInfo.getId());
                bridgeSubject.onNext(new SourcedChangeNotification<>(Kind.Add, instanceInfo, selfSource));
                registerCount.incrementAndGet();
            }
        }

        currentEureka1Snapshot.keySet().removeAll(newSnapshot.keySet());
        for (InstanceInfo instanceInfo : currentEureka1Snapshot.values()) {
            logger.info("Unregistering IInstanceInfo {} data from Eureka1 registry", instanceInfo.getId());
            bridgeSubject.onNext(new SourcedChangeNotification<>(Kind.Delete, instanceInfo, selfSource));
            unregisterCount.incrementAndGet();
        }

        bridgeSubject.onNext(bufferEnd);

        logger.info("Finished new round of replication from v1 to v2." +
                        " Total: {}, registers: {}, updates: {}, unregisters: {}",
                totalCount.get(), registerCount.get(), updateCount.get(), unregisterCount.get());
        metrics.setTotalCount(totalCount.get());
        metrics.setRegisterCount(registerCount.get());
        metrics.setUpdateCount(updateCount.get());
        metrics.setUnregisterCount(unregisterCount.get());
    }

    private List<InstanceInfo> getNewEureka1Snapshot() {
        List<Application> applications = discoveryClient.getApplications().getRegisteredApplications();
        List<InstanceInfo> v1InstanceInfos = new ArrayList<>();
        for (Application app : applications) {
            for (com.netflix.appinfo.InstanceInfo v1Instance : app.getInstances()) {
                v1InstanceInfos.add(toEureka2xInstanceInfo(v1Instance));
            }
        }
        return v1InstanceInfos;
    }

    private Map<String, InstanceInfo> getCurrentEureka1Snapshot() {
        return registry.forSnapshot(Interests.forFullRegistry(), Source.matcherFor(Origin.REPLICATED, SOURCE_NAME))
                .toMap(new Func1<InstanceInfo, String>() {
                    @Override
                    public String call(InstanceInfo instanceInfo) {
                        return instanceInfo.getId();
                    }
                })
                .toBlocking().first();
    }
}
