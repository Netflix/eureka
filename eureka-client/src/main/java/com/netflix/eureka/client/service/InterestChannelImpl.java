package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.discovery.DiscoveryClient;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import rx.Observable;

/**
 * An implementation of {@link com.netflix.eureka.service.InterestChannel}
 *
 * @author Nitesh Kant
 */
public class InterestChannelImpl extends AbstractChannel implements InterestChannel {

    private final DiscoveryClient discoveryClient; // TODO: Is this the correct abstraction to use?
    private final Observable<ChangeNotification<InstanceInfo>> interestStream;

    public InterestChannelImpl(DiscoveryClient discoveryClient, Observable<ChangeNotification<InstanceInfo>> interestStream) {
        super(30000);
        this.discoveryClient = discoveryClient;
        this.interestStream = interestStream;
    }

    @Override
    public Observable<Void> upgrade(Interest<InstanceInfo> newInterest) {
        return Observable.error(new UnsupportedOperationException("Upgrade not yet implemented."));
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> asObservable() {
        return interestStream;
    }

    @Override
    public void heartbeat() {
        //TODO: Send heartbeat
    }
}
