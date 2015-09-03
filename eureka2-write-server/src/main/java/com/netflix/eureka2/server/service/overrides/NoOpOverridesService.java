package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author David Liu
 */
@Singleton
public class NoOpOverridesService implements OverridesService {

    private volatile EurekaRegistrationProcessor<InstanceInfo> delegate;

    @Inject
    public NoOpOverridesService() {
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
        return delegate.connect(id, source, registrationUpdates);
    }

    @Override
    public Observable<Void> shutdown() {
        return delegate.shutdown();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        return delegate.shutdown(cause);
    }

    @Override
    public Observable<Integer> sizeObservable() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }
}
