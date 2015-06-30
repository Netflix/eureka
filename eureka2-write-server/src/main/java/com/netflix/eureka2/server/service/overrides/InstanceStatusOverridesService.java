package com.netflix.eureka2.server.service.overrides;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.RxFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

/**
 * An overrides service implementation that loads overrides from a specified override source.
 *
 * @author Tomasz Bak
 */
@Singleton
public class InstanceStatusOverridesService implements OverridesService {

    private static final Logger logger = LoggerFactory.getLogger(InstanceStatusOverridesService.class);

    private final InstanceStatusOverridesView overridesRegistry;
    private volatile EurekaRegistrationProcessor<InstanceInfo> delegate;

    @Inject
    public InstanceStatusOverridesService(InstanceStatusOverridesView overridesRegistry) {
        this.overridesRegistry = overridesRegistry;
    }

    @Override
    public void addOutboundHandler(EurekaRegistrationProcessor<InstanceInfo> delegate) {
        if (this.delegate != null) {
            throw new IllegalStateException("Second outbound handler injection not allowed");
        }
        this.delegate = delegate;
    }

    @Override
    public Observable<Void> register(String id, Observable<InstanceInfo> registrationUpdates, Source source) {
        Observable<InstanceInfo> sharedUpdates = registrationUpdates.cache(1);

        Observable<Boolean> asgOverrides = sharedUpdates
                .take(1)
                .concatMap(new Func1<InstanceInfo, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(InstanceInfo instanceInfo) {
                        return overridesRegistry.shouldApplyOutOfService(instanceInfo);
                    }
                });

        Observable<InstanceInfo> overriddenUpdates = RxFunctions.combineWithOptional(
                sharedUpdates,
                asgOverrides,
                new Func2<InstanceInfo, Boolean, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(InstanceInfo instanceInfo, Boolean shouldApply) {
                        if (!shouldApply || instanceInfo.getStatus() == InstanceInfo.Status.DOWN) {
                            return instanceInfo;
                        }
                        return new InstanceInfo.Builder().withInstanceInfo(instanceInfo).withStatus(InstanceInfo.Status.OUT_OF_SERVICE).build();
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
}
