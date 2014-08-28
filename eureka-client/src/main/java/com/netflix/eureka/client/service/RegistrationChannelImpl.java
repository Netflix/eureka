package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.registration.RegistrationClient;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.RegistrationChannel;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;

/**
 * @author Nitesh Kant
 */
public class RegistrationChannelImpl extends AbstractChannel implements RegistrationChannel {

    //TODO: How to guarantee ordering between actions?

    private final RegistrationClient registrationClient; // TODO: Is this the correct abstraction to use?
    private volatile InstanceInfo currentInfo;

    public RegistrationChannelImpl(RegistrationClient registrationClient) {
        super(30000);
        this.registrationClient = registrationClient;
    }

    @Override
    public Observable<Void> register(final InstanceInfo newInfo) {
        return registrationClient.register(newInfo).doOnCompleted(new Action0() {
            @Override
            public void call() {
                currentInfo = newInfo;
            }
        });
    }

    @Override
    public Observable<Void> update(final InstanceInfo newInfo) {
        return registrationClient.update(newInfo)
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        currentInfo = newInfo;
                    }
                });
    }

    @Override
    public void _close() {
        registrationClient.unregister(currentInfo);
        registrationClient.shutdown();
    }

    @Override
    public void heartbeat() {
        registrationClient.heartbeat(currentInfo).doOnError(new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                close(throwable);
            }
        });
    }
}
