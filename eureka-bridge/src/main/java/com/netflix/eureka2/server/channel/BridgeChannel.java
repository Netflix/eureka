package com.netflix.eureka2.server.channel;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.server.bridge.InstanceInfoConverter;
import com.netflix.eureka2.server.bridge.InstanceInfoConverterImpl;
import com.netflix.eureka2.server.bridge.OperatorInstanceInfoFromV1;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.server.channel.BridgeChannel.STATES;
import com.netflix.eureka2.server.metric.BridgeChannelMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A bridge channel that handles changes between snapshots of v1 instanceInfo
 *
 * @author David Liu
 */
public class BridgeChannel extends AbstractHandlerChannel<STATES> {
    private static final Logger logger = LoggerFactory.getLogger(BridgeChannel.class);

    public enum STATES {Opened, Closed}

    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final DiscoveryClient discoveryClient;
    private final InstanceInfoConverter converter;
    private final BridgeChannelMetrics metrics;
    private final int refreshRateSec;
    private final InstanceInfo self;
    private final Scheduler.Worker worker;

    private final Subject<Void, Void> lifecycle;

    public BridgeChannel(SourcedEurekaRegistry<InstanceInfo> registry,
                         DiscoveryClient discoveryClient,
                         int refreshRateSec,
                         InstanceInfo self,
                         BridgeChannelMetrics metrics)
    {
        this(registry, discoveryClient, refreshRateSec, self, metrics, Schedulers.computation());
    }

    public BridgeChannel(SourcedEurekaRegistry<InstanceInfo> registry,
                         DiscoveryClient discoveryClient,
                         int refreshRateSec,
                         InstanceInfo self,
                         BridgeChannelMetrics metrics,
                         Scheduler scheduler)
    {
        super(STATES.Opened, null, registry);
        this.registry = registry;
        this.discoveryClient = discoveryClient;
        this.refreshRateSec = refreshRateSec;
        this.self = self;
        this.metrics = metrics;

        converter = new InstanceInfoConverterImpl();
        worker = scheduler.createWorker();

        lifecycle = ReplaySubject.create();
    }

    public void connect() {
        worker.schedulePeriodically(new Action0() {
            @Override
            public void call() {
                logger.info("Starting new round of replication from v1 to v2");
                registry.forSnapshot(Interests.forFullRegistry(), Source.localSource())
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
        }, 0, refreshRateSec, TimeUnit.SECONDS);
    }

    /**
     * Since the bridge registry take no other forms of input other than those from this bridge channel,
     * this snapshot view of the current registry should be static w.r.t. to the channel operation.
     */

    protected void diff(final Map<String, InstanceInfo> currentSnapshot) {
        final AtomicLong totalCount = new AtomicLong(0);
        final AtomicLong updateCount = new AtomicLong(0);
        final AtomicLong registerCount = new AtomicLong(0);
        final AtomicLong unregisterCount = new AtomicLong(0);

        final Map<String, InstanceInfo> newSnapshot = new HashMap<>();

        getV1Stream()
                .lift(new OperatorInstanceInfoFromV1(converter))
                .subscribe(new Subscriber<InstanceInfo>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.warn("error processing v1 stream", e);
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(InstanceInfo instanceInfo) {
                        totalCount.incrementAndGet();
                        newSnapshot.put(instanceInfo.getId(), instanceInfo);
                        if (currentSnapshot.containsKey(instanceInfo.getId())) {
                            InstanceInfo older = currentSnapshot.get(instanceInfo.getId());
                            if (!older.equals(instanceInfo)) {
                                registry.register(instanceInfo);
                                updateCount.incrementAndGet();
                            }
                        } else {
                            registry.register(instanceInfo);
                            registerCount.incrementAndGet();
                        }
                    }
                });

        currentSnapshot.keySet().removeAll(newSnapshot.keySet());
        Observable.from(currentSnapshot.values())
                .subscribe(new Subscriber<InstanceInfo>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.warn("error unregistering from v1 stream", e);
                    }

                    @Override
                    public void onNext(InstanceInfo instanceInfo) {
                        registry.unregister(instanceInfo);
                        unregisterCount.incrementAndGet();
                    }
                });

        logger.info("Finished new round of replication from v1 to v2." +
                        " Total: {}, registers: {}, updates: {}, unregisters: {}",
                totalCount.get(), registerCount.get(), updateCount.get(), unregisterCount.get());
        metrics.setTotalCount(totalCount.get());
        metrics.setRegisterCount(registerCount.get());
        metrics.setUpdateCount(updateCount.get());
        metrics.setUnregisterCount(unregisterCount.get());
    }

    protected Observable<com.netflix.appinfo.InstanceInfo> getV1Stream() {
        Applications applications =
                new Applications(discoveryClient.getApplications().getRegisteredApplications());

        return Observable.from(applications.getRegisteredApplications())
                .flatMap(new Func1<Application, Observable<com.netflix.appinfo.InstanceInfo>>() {
                    @Override
                    public Observable<com.netflix.appinfo.InstanceInfo> call(Application application) {
                        return Observable.from(application.getInstances());
                    }
                });
    }

    @Override
    protected void _close() {
        discoveryClient.shutdown();
        registry.shutdown();
        lifecycle.onCompleted();
    }
}
