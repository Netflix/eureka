package com.netflix.eureka2.server.service.overrides;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.SerializedTaskInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;

/**
 * @author Tomasz Bak
 */
@Singleton
public class OverridesServiceImpl extends SerializedTaskInvoker implements OverridesService {

    private static final Logger logger = LoggerFactory.getLogger(OverridesServiceImpl.class);

    private static final Observable<Boolean> JUST_FALSE = Observable.just(false);

    private final SourcedEurekaRegistry<InstanceInfo> delegate;
    private final OverridesRegistry overridesRegistry;

    private final ConcurrentMap<String, InstanceWithSource> instanceCache = new ConcurrentHashMap<>();

    private final Subscription overrideUpdateSubscription;
    private final Subscription registryLocalUpdateSubscription;

    @Inject
    public OverridesServiceImpl(SourcedEurekaRegistry<InstanceInfo> delegate,
                                OverridesRegistry overridesRegistry,
                                WriteServerMetricFactory metricFactory) {
        super(metricFactory.getOverrideServiceTaskInvokerMetrics());
        this.delegate = delegate;
        this.overridesRegistry = overridesRegistry;

        this.overrideUpdateSubscription = subscribeToOverrideUpdates();
        this.registryLocalUpdateSubscription = subscribeToLocalUpdates();
    }

    @Override
    public Observable<Boolean> register(final InstanceInfo instanceInfo, final Source source) {
        return submitForResult(new Callable<Observable<Boolean>>() {
            @Override
            public Observable<Boolean> call() throws Exception {
                return delegate.register(applyDelta(instanceInfo), source)
                        .doOnCompleted(new Action0() {
                            @Override
                            public void call() {
                                instanceCache.put(instanceInfo.getId(), new InstanceWithSource(instanceInfo, source, true));
                            }
                        });
            }
        });
    }

    @Override
    public Observable<Boolean> unregister(final InstanceInfo instanceInfo, final Source source) {
        return submitForResult(new Callable<Observable<Boolean>>() {
            @Override
            public Observable<Boolean> call() throws Exception {
                return delegate.unregister(instanceInfo, source)
                        .doOnCompleted(new Action0() {
                            @Override
                            public void call() {
                                instanceCache.put(instanceInfo.getId(), new InstanceWithSource(instanceInfo, source, false));
                            }
                        });
            }
        });
    }

    @Override
    public Observable<Void> shutdown() {
        overrideUpdateSubscription.unsubscribe();
        registryLocalUpdateSubscription.unsubscribe();
        shutdownTaskInvoker();
        return Observable.empty();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        return Observable.empty();
    }

    private Subscription subscribeToOverrideUpdates() {
        return overridesRegistry.forUpdates().subscribe(new Subscriber<ChangeNotification<Overrides>>() {
            @Override
            public void onCompleted() {
                logger.info("Overrides update stream onCompleted");
            }

            @Override
            public void onError(Throwable e) {
                logger.error("Overrides update stream terminated with an error", e);
            }

            @Override
            public void onNext(final ChangeNotification<Overrides> notification) {
                submitForResult(new Callable<Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call() throws Exception {
                        switch (notification.getKind()) {
                            case Add:
                            case Modify:
                                return handleOverrideAdd(notification.getData());
                            case Delete:
                                return handleOverrideDelete(notification.getData());
                        }
                        return Observable.error(new IllegalStateException("unrecognized notification type " + notification.getKind()));
                    }
                }).subscribe(new Subscriber<Boolean>() {
                                 @Override
                                 public void onCompleted() {
                                     // Ignore
                                 }

                                 @Override
                                 public void onError(Throwable e) {
                                     logger.warn("Registry updated triggered by override failed", e);
                                 }

                                 @Override
                                 public void onNext(Boolean result) {
                                     // Ignore
                                 }
                             }
                );
            }
        });
    }

    private Subscription subscribeToLocalUpdates() {
        return delegate.forInterest(Interests.forFullRegistry(), Source.matcherFor(Origin.LOCAL))
                .subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        logger.info("Subscription to registry local updates onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("Subscription to registry local updates terminated with an error", e);
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        if (notification.getKind() == Kind.Delete) {
                            instanceCache.remove(notification.getData().getId());
                        }
                    }
                });
    }

    private InstanceInfo applyDelta(InstanceInfo instanceInfo) {
        Set<Delta<?>> deltas = overridesRegistry.get(instanceInfo.getId());
        InstanceInfo toRegister = instanceInfo;
        if (deltas != null) {
            for (Delta<?> delta : deltas) {
                toRegister = toRegister.applyDelta(delta);
            }
        }
        return toRegister;
    }

    private Observable<Boolean> handleOverrideAdd(Overrides overrides) {
        String id = overrides.getId();
        InstanceWithSource cachedItem = instanceCache.get(id);
        if (cachedItem == null) {
            return JUST_FALSE;
        }
        InstanceInfo instanceWithOverrides = new InstanceInfo.Builder().withInstanceInfo(cachedItem.getInstanceInfo()).build();
        for (Delta<?> delta : overrides.getDeltas()) {
            instanceWithOverrides = instanceWithOverrides.applyDelta(delta);
        }
        if (cachedItem.isLive()) {
            return delegate.register(instanceWithOverrides, cachedItem.getSource());
        }
        // The item is dead, but still in the registry due to self preservation mode so we need to maintain it.
        // The only way to do this with current registry API is to do registration, followed by unregister.
        return delegate.register(instanceWithOverrides, cachedItem.getSource()).concatWith(
                unregister(instanceWithOverrides, cachedItem.getSource())
        ).takeLast(1);
    }

    private Observable<Boolean> handleOverrideDelete(Overrides overrides) {
        String id = overrides.getId();
        InstanceWithSource cachedItem = instanceCache.get(id);
        if (cachedItem == null) {
            return JUST_FALSE;
        }
        return delegate.register(cachedItem.getInstanceInfo(), cachedItem.getSource());
    }

    static class InstanceWithSource {
        private final InstanceInfo instanceInfo;
        private final Source source;
        private final boolean live;

        InstanceWithSource(InstanceInfo instanceInfo, Source source, boolean live) {
            this.instanceInfo = instanceInfo;
            this.source = source;
            this.live = live;
        }

        public InstanceInfo getInstanceInfo() {
            return instanceInfo;
        }

        public Source getSource() {
            return source;
        }

        public boolean isLive() {
            return live;
        }
    }
}
