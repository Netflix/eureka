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
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.metric.server.BridgeChannelMetrics;
import com.netflix.eureka2.metric.server.BridgeServerMetricFactory;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka2xInstanceInfo;

/**
 */
@Singleton
public class Eureka1Bridge {

    private static final Logger logger = LoggerFactory.getLogger(Eureka1Bridge.class);

    private static final String SOURCE_NAME = "eureka1Bridge";

    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final DiscoveryClient discoveryClient;
    private final Eureka1BridgeConfiguration config;
    private final BridgeChannelMetrics metrics;
    private final Source selfSource;
    private final Worker worker;

    public Eureka1Bridge(SourcedEurekaRegistry<InstanceInfo> registry,
                         DiscoveryClient discoveryClient,
                         Eureka1BridgeConfiguration config,
                         BridgeServerMetricFactory metricsFactory,
                         Scheduler scheduler) {
        this.registry = registry;
        this.discoveryClient = discoveryClient;
        this.config = config;
        this.metrics = metricsFactory.getBridgeChannelMetrics();
        this.selfSource = new Source(Origin.REPLICATED, SOURCE_NAME);
        this.worker = scheduler.createWorker();
    }

    @Inject
    public Eureka1Bridge(SourcedEurekaRegistry registry,
                         DiscoveryClient discoveryClient,
                         Eureka1BridgeConfiguration config,
                         BridgeServerMetricFactory metricsFactory) {
        this(registry, discoveryClient, config, metricsFactory, Schedulers.io());
    }

    @PostConstruct
    public void start() {
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
        worker.unsubscribe();
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
        for (InstanceInfo instanceInfo : latestV1Instances) {
            totalCount.incrementAndGet();
            newSnapshot.put(instanceInfo.getId(), instanceInfo);
            if (currentEureka1Snapshot.containsKey(instanceInfo.getId())) {
                InstanceInfo older = currentEureka1Snapshot.get(instanceInfo.getId());
                if (!older.equals(instanceInfo)) {
                    logger.info("Updating InstanceInfo {} data from Eureka1 registry", instanceInfo.getId());
                    registry.register(instanceInfo, selfSource);
                    updateCount.incrementAndGet();
                }
            } else {
                logger.info("Registering new InstanceInfo {} data from Eureka1 registry", instanceInfo.getId());
                registry.register(instanceInfo, selfSource);
                registerCount.incrementAndGet();
            }
        }

        currentEureka1Snapshot.keySet().removeAll(newSnapshot.keySet());
        for (InstanceInfo instanceInfo : currentEureka1Snapshot.values()) {
            logger.info("Unregistering InstanceInfo {} data from Eureka1 registry", instanceInfo.getId());
            registry.unregister(instanceInfo, selfSource);
            unregisterCount.incrementAndGet();
        }

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
