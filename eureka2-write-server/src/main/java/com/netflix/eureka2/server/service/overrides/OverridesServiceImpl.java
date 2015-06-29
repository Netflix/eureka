package com.netflix.eureka2.server.service.overrides;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.RxFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

/**
 * @author Tomasz Bak
 */
@Singleton
public class OverridesServiceImpl implements OverridesService {

    private static final Logger logger = LoggerFactory.getLogger(OverridesServiceImpl.class);

    private final EurekaRegistrationProcessor<InstanceInfo> delegate;
    private final OverridesRegistry overridesRegistry;

    @Inject
    public OverridesServiceImpl(EurekaRegistrationProcessor<InstanceInfo> delegate, OverridesRegistry overridesRegistry) {
        this.delegate = delegate;
        this.overridesRegistry = overridesRegistry;
    }

    @Override
    public Observable<Void> register(String id, Observable<InstanceInfo> registrationUpdates, Source source) {
        Observable<InstanceInfo> sharedUpdates = registrationUpdates.cache(1);

        Observable<ChangeNotification<Overrides>> instanceOverrides = sharedUpdates
                .take(1)
                .concatMap(new Func1<InstanceInfo, Observable<ChangeNotification<Overrides>>>() {
                    @Override
                    public Observable<ChangeNotification<Overrides>> call(InstanceInfo instanceInfo) {
                        return overridesRegistry.forUpdates(instanceInfo.getId());
                    }
                });

        Observable<InstanceInfo> overriddenUpdates = RxFunctions.combineWithOptional(
                sharedUpdates,
                instanceOverrides,
                new Func2<InstanceInfo, ChangeNotification<Overrides>, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(InstanceInfo instanceInfo, ChangeNotification<Overrides> overrideNotification) {
                        switch (overrideNotification.getKind()) {
                            case Add:
                            case Modify:
                                logger.info("Applying overrides to instance {}: {}", instanceInfo.getId(), overrideNotification.getData().getDeltas());
                                return applyDelta(instanceInfo, overrideNotification.getData().getDeltas());
                            case Delete:
                                logger.info("Removed all overrides for instance {}", instanceInfo.getId());
                                return instanceInfo;
                        }
                        throw new IllegalStateException("Unexpected notification kind " + overrideNotification.getKind());
                    }
                });

        return delegate.register(id, overriddenUpdates, source);
    }

    @Override
    public Observable<Boolean> register(final InstanceInfo instanceInfo, final Source source) {
        throw new IllegalStateException("method not implemented");
    }

    @Override
    public Observable<Boolean> unregister(final InstanceInfo instanceInfo, final Source source) {
        throw new IllegalStateException("method not implemented");
    }

    @Override
    public Observable<Void> shutdown() {
        return Observable.empty();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        return Observable.empty();
    }

    private static InstanceInfo applyDelta(InstanceInfo instanceInfo, Set<Delta<?>> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return instanceInfo;
        }
        InstanceInfo merged = instanceInfo;
        for (Delta<?> delta : deltas) {
            merged = merged.applyDelta(delta);
        }
        return merged;
    }
}
