package com.netflix.eureka2.channel;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * @author David Liu
 */
public class TestRegistrationChannel extends TestChannel<RegistrationChannel, InstanceInfo> implements RegistrationChannel {
    public volatile boolean hasUnregistered;

    public TestRegistrationChannel(RegistrationChannel delegate, Integer id) {
        super(delegate, id);
        this.hasUnregistered = false;
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        operations.add(instanceInfo);
        return delegate.register(instanceInfo);
    }

    @Override
    public Observable<Void> unregister() {
        hasUnregistered = true;
        return delegate.unregister();
    }
}
