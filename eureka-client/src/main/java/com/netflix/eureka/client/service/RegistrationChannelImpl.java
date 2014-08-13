package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.registration.RegistrationClient;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.RegistrationChannel;
import rx.Observable;
import rx.functions.Action0;

/**
 * @author Nitesh Kant
 */
public class RegistrationChannelImpl extends AbstractChannel implements RegistrationChannel {

    private final RegistrationClient registrationClient; // TODO: Is this the correct abstraction to use?
    private volatile InstanceInfo currentInfo;

    public RegistrationChannelImpl(RegistrationClient registrationClient, InstanceInfo instanceInfo) {
        super(30000);
        this.registrationClient = registrationClient;
        this.currentInfo = instanceInfo;
    }

    @Override
    public Observable<Void> update(final InstanceInfo newInfo) {
        return registrationClient.update(newInfo, new Update("", ""))
                                 .doOnCompleted(new Action0() {
                                     @Override
                                     public void call() {
                                         currentInfo = newInfo;
                                     }
                                 }); // TODO: do diff
    }

    @Override
    public void _close() {
        registrationClient.unregister(currentInfo);
    }

    @Override
    public void heartbeat() {
        // TODO: send heartbeat
    }
}
