package com.netflix.eureka2.server.service.overrides;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.utils.functions.RxFunctions;
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
    public Observable<Void> connect(String id, Source source, Observable<ChangeNotification<InstanceInfo>> registrationUpdates) {
        Observable<ChangeNotification<InstanceInfo>> sharedUpdates = registrationUpdates.cache(1);

        Observable<Boolean> asgOverrides = sharedUpdates
                .take(1)
                .concatMap(new Func1<ChangeNotification<InstanceInfo>, Observable<? extends Boolean>>() {
                    @Override
                    public Observable<? extends Boolean> call(ChangeNotification<InstanceInfo> notification) {
                        if (notification.isDataNotification() && notification.getKind() != ChangeNotification.Kind.Delete) {
                            InstanceInfo instanceInfo = notification.getData();
                            return overridesRegistry.shouldApplyOutOfService(instanceInfo);
                        }
                        return Observable.just(false);
                    }
                });

        Observable<ChangeNotification<InstanceInfo>> overriddenUpdates = RxFunctions.combineWithOptional(
                sharedUpdates,
                asgOverrides,
                new Func2<ChangeNotification<InstanceInfo>, Boolean, ChangeNotification<InstanceInfo>>() {
                    @Override
                    public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification, Boolean shouldApply) {
                        if (notification.isDataNotification()) {
                            if (shouldApply && notification.getData().getStatus() != InstanceInfo.Status.DOWN) {
                                logger.debug("Adding override for instanceId {}", notification.getData().getId());
                                InstanceInfo newInfo = InstanceModel.getDefaultModel().newInstanceInfo()
                                        .withInstanceInfo(notification.getData())
                                        .withStatus(InstanceInfo.Status.OUT_OF_SERVICE)
                                        .build();
                                return new ChangeNotification<>(notification.getKind(), newInfo);
                            }
                        }

                        // else
                        return notification;
                    }
                });

        return delegate.connect(id, source, overriddenUpdates);
    }

    @Override
    public Observable<Integer> sizeObservable() {
        return delegate.sizeObservable();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public Observable<Void> shutdown() {
        return delegate.shutdown();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        return delegate.shutdown(cause);
    }
}
