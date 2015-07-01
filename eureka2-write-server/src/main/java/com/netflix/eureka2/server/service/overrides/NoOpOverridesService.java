package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
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
    public Observable<Void> register(String id, Observable<InstanceInfo> registrationUpdates, Source source) {
        return delegate.register(id, registrationUpdates, source);
    }

    @Override
    public Observable<Boolean> register(InstanceInfo registrant, Source source) {
        return delegate.register(registrant, source);
    }

    @Override
    public Observable<Boolean> unregister(InstanceInfo registrant, Source source) {
        return delegate.unregister(registrant, source);
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
