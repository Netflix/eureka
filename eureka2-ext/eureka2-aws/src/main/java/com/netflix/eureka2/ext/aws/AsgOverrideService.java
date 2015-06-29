package com.netflix.eureka2.ext.aws;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.server.service.overrides.OverridesService;
import com.netflix.eureka2.utils.rx.RxFunctions;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

/**
 * Override service loading ASG status from AWS.
 *
 * @author Tomasz Bak
 */
@Singleton
public class AsgOverrideService implements OverridesService {


    private final AsgStatusRegistry asgStatusRegistry;
    private volatile EurekaRegistrationProcessor<InstanceInfo> delegate;

    @Inject
    public AsgOverrideService(AsgStatusRegistry asgStatusRegistry) {
        this.asgStatusRegistry = asgStatusRegistry;
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
                        return asgStatusRegistry.asgStatusUpdates(instanceInfo.getAsg());
                    }
                });

        Observable<InstanceInfo> overridenUpdates = RxFunctions.combineWithOptional(
                sharedUpdates,
                asgOverrides,
                new Func2<InstanceInfo, Boolean, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(InstanceInfo instanceInfo, Boolean asgOpen) {
                        if (asgOpen || instanceInfo.getStatus() == Status.DOWN) {
                            return instanceInfo;
                        }
                        return new InstanceInfo.Builder().withInstanceInfo(instanceInfo).withStatus(Status.OUT_OF_SERVICE).build();
                    }
                });

        return delegate.register(id, overridenUpdates, source);
    }

    @Override
    public Observable<Boolean> register(InstanceInfo registrant, Source source) {
        throw new IllegalStateException("method not implemented");
    }

    @Override
    public Observable<Boolean> unregister(InstanceInfo registrant, Source source) {
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
