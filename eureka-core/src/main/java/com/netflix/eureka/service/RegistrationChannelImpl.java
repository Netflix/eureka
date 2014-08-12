package com.netflix.eureka.service;

import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
public class RegistrationChannelImpl extends AbstractChannel implements RegistrationChannel {

    @Override
    public Observable<Void> update(InstanceInfo newInfo) {
        // TODO: Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO: Auto-generated method stub

    }
}
