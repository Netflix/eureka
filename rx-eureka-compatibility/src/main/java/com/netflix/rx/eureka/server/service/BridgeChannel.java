package com.netflix.rx.eureka.server.service;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.rx.eureka.compatibility.InstanceInfoConverter;
import com.netflix.rx.eureka.compatibility.InstanceInfoConverterImpl;
import com.netflix.rx.eureka.compatibility.OperatorInstanceInfoFromV1;
import com.netflix.rx.eureka.registry.Delta;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.service.ServiceChannel;
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
public class BridgeChannel implements ServiceChannel {
    private static final Logger logger = LoggerFactory.getLogger(BridgeChannel.class);

    private final EurekaRegistry<InstanceInfo> registry;
    private final DiscoveryClient discoveryClient;
    private final InstanceInfoConverter converter;
    private final int refreshRateSec;
    private final Scheduler.Worker worker;
    private Map<String, InstanceInfo> currentSnapshot;

    private final Subject<Void, Void> lifecycle;

    public BridgeChannel(EurekaRegistry<InstanceInfo> registry,
                         DiscoveryClient discoveryClient,
                         int refreshRateSec) {
        this.registry =registry;
        this.discoveryClient = discoveryClient;
        this.refreshRateSec = refreshRateSec;

        converter = new InstanceInfoConverterImpl();
        worker = Schedulers.computation().createWorker();
        currentSnapshot = new HashMap<>();

        lifecycle = ReplaySubject.create();
    }

    public void connect() {
        worker.schedulePeriodically(new Action0() {
            @Override
            public void call() {
                logger.info("Starting new round of replication from v1 to v2");
                final AtomicLong totalCount = new AtomicLong(0);  // TODO: servo-fy
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
                                        Set<Delta<?>> deltas = older.diffNewer(instanceInfo);
                                        registry.update(instanceInfo, deltas);
                                        updateCount.incrementAndGet();
                                    }
                                } else {
                                    registry.register(instanceInfo);
                                    registerCount.incrementAndGet();
                                }
                            }
                        });

                currentSnapshot.keySet().removeAll(newSnapshot.keySet());
                Observable.from(currentSnapshot.keySet())
                        .subscribe(new Subscriber<String>() {
                            @Override
                            public void onCompleted() {}

                            @Override
                            public void onError(Throwable e) {
                                logger.warn("error unregistering from v1 stream", e);
                                e.printStackTrace();
                            }

                            @Override
                            public void onNext(String instanceId) {
                                registry.unregister(instanceId);
                                unregisterCount.incrementAndGet();
                            }
                        });

                currentSnapshot = newSnapshot;

                logger.info("Finished new round of replication from v1 to v2." +
                        " Total: {}, registers: {}, updates: {}, unregisters: {}",
                        totalCount.get(), registerCount.get(), updateCount.get(), unregisterCount.get());
            }
        }, 0, refreshRateSec, TimeUnit.SECONDS);
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
    public void close() {
        discoveryClient.shutdown();
        registry.shutdown();
        lifecycle.onCompleted();
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        return lifecycle;
    }
}
