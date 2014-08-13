package com.netflix.eureka.server.service;

import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.RegistrationChannel;
import rx.Observable;
import rx.functions.Action0;

/**
 * @author Nitesh Kant
 */
public class RegistrationChannelImpl extends AbstractChannel implements RegistrationChannel {

    private final EurekaRegistry registry;
    private volatile InstanceInfo currentInfo;

    public RegistrationChannelImpl(EurekaRegistry registry) {
        super(3, 30000);
        this.registry = registry;
    }

    @Override
    public Observable<Void> update(final InstanceInfo newInfo) {
        // TODO: Where does ordering of UPDATE -> ACK -> UPDATE happen? So that we don't do UPDATE -> UPDATE -> ACK -> ACK
        return registry.update(newInfo)
                       .doOnCompleted(new Action0() {
                           @Override
                           public void call() {
                               currentInfo = newInfo;
                           }
                       });
    }

    @Override
    public void _close() {
        registry.unregister(currentInfo.getId());
    }
}
