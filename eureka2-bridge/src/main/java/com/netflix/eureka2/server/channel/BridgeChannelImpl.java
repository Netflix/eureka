package com.netflix.eureka2.server.channel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.channel.BridgeChannel;
import com.netflix.eureka2.channel.BridgeChannel.STATE;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.metric.server.BridgeChannelMetrics;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.bridge.InstanceInfoConverter;
import com.netflix.eureka2.server.bridge.InstanceInfoConverterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

/**
 * A bridge channel that handles changes between snapshots of v1 instanceInfo
 *
 * @author David Liu
 */
public class BridgeChannelImpl extends AbstractHandlerChannel<STATE> implements BridgeChannel {
    private static final Logger logger = LoggerFactory.getLogger(BridgeChannelImpl.class);

    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final DiscoveryClient discoveryClient;
    private final InstanceInfoConverter converter;
    private final BridgeChannelMetrics metrics;
    private final int refreshRateSec;
    private final InstanceInfo self;
    private final Source selfSource;
    private final Scheduler.Worker worker;

    private final Subject<Void, Void> lifecycle;

    public BridgeChannelImpl(SourcedEurekaRegistry<InstanceInfo> registry,
                             DiscoveryClient discoveryClient,
                             int refreshRateSec,
                             InstanceInfo self,
                             BridgeChannelMetrics metrics) {
        this(registry, discoveryClient, refreshRateSec, self, metrics, Schedulers.io());
    }

    public BridgeChannelImpl(SourcedEurekaRegistry<InstanceInfo> registry,
                             DiscoveryClient discoveryClient,
                             int refreshRateSec,
                             InstanceInfo self,
                             BridgeChannelMetrics metrics,
                             Scheduler scheduler) {
        super(STATE.Idle, null, registry, metrics);
        this.registry = registry;
        this.discoveryClient = discoveryClient;
        this.refreshRateSec = refreshRateSec;
        this.self = self;
        this.selfSource = new Source(Source.Origin.LOCAL);
        this.metrics = metrics;

        converter = new InstanceInfoConverterImpl();
        worker = scheduler.createWorker();

        lifecycle = ReplaySubject.create();
    }

    @Override
    public Source getSource() {
        return selfSource;
    }

    @Override
    public void connect() {
        if (!moveToState(STATE.Idle, STATE.Opened)) {
            return;
        }
        worker.schedulePeriodically(
                new Action0() {
                    @Override
                    public void call() {
                        loadUpdatesFromV1Registry();
                    }
                }, 0, refreshRateSec, TimeUnit.SECONDS);
    }

    private void loadUpdatesFromV1Registry() {
        logger.info("Starting new round of replication from v1 to v2");
        registry.forSnapshot(Interests.forFullRegistry(), Source.matcherFor(Source.Origin.LOCAL))
                .filter(new Func1<InstanceInfo, Boolean>() {  // filter self so it's not take into account
                    @Override
                    public Boolean call(InstanceInfo instanceInfo) {
                        return !instanceInfo.getId().equals(self.getId());
                    }
                })
                .toMap(new Func1<InstanceInfo, String>() {
                    @Override
                    public String call(InstanceInfo instanceInfo) {
                        return instanceInfo.getId();
                    }
                })
                .subscribe(new Subscriber<Map<String, InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.warn("Error generating snapshot of registry", e);
                    }

                    @Override
                    public void onNext(Map<String, InstanceInfo> currentSnapshot) {
                        diff(currentSnapshot);
                    }
                });
    }

    /**
     * Since the bridge registry take no other forms of input other than those from this bridge channel,
     * this snapshot view of the current registry should be static w.r.t. to the channel operation.
     */
    private void diff(final Map<String, InstanceInfo> currentSnapshot) {
        final AtomicInteger totalCount = new AtomicInteger(0);
        final AtomicInteger updateCount = new AtomicInteger(0);
        final AtomicInteger registerCount = new AtomicInteger(0);
        final AtomicInteger unregisterCount = new AtomicInteger(0);

        final Map<String, InstanceInfo> newSnapshot = new HashMap<>();

        List<InstanceInfo> latestV1Instances = getLatestV1Instances();
        for (InstanceInfo instanceInfo : latestV1Instances) {
            totalCount.incrementAndGet();
            newSnapshot.put(instanceInfo.getId(), instanceInfo);
            if (currentSnapshot.containsKey(instanceInfo.getId())) {
                InstanceInfo older = currentSnapshot.get(instanceInfo.getId());
                if (!older.equals(instanceInfo)) {
                    registry.register(instanceInfo, selfSource);
                    updateCount.incrementAndGet();
                }
            } else {
                registry.register(instanceInfo, selfSource);
                registerCount.incrementAndGet();
            }
        }

        currentSnapshot.keySet().removeAll(newSnapshot.keySet());
        for (InstanceInfo instanceInfo : currentSnapshot.values()) {
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

    protected List<InstanceInfo> getLatestV1Instances() {
        Applications applications =
                new Applications(discoveryClient.getApplications().getRegisteredApplications());
        List<InstanceInfo> v1InstanceInfos = new ArrayList<>();
        for (Application app : applications.getRegisteredApplications()) {
            for (com.netflix.appinfo.InstanceInfo v1Instance : app.getInstances()) {
                v1InstanceInfos.add(converter.fromV1(v1Instance));
            }
        }
        return v1InstanceInfos;
    }

    @Override
    protected void _close() {
        discoveryClient.shutdown();
        lifecycle.onCompleted();
    }
}
