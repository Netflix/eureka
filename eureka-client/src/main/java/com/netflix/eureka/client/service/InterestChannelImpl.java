package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.discovery.DiscoveryClient;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import rx.Observable;
import rx.functions.Action1;

/**
 * An implementation of {@link com.netflix.eureka.service.InterestChannel}
 *
 * @author Nitesh Kant
 */
public class InterestChannelImpl extends AbstractChannel implements InterestChannel {

    private final DiscoveryClient discoveryClient;
    private final Observable<ChangeNotification<InstanceInfo>> interestStream;

    public InterestChannelImpl(DiscoveryClient discoveryClient) {
        super(30000);
        this.discoveryClient = discoveryClient;
        this.interestStream = discoveryClient.updates();
    }

    @Override
    public Observable<Void> upgrade(Interest<InstanceInfo> newInterest) {
        /**
         * TODO: Upgrades have two states:
         * 1) Every interest is unique: Server re-runs from the start for every interest.
         * 2) Overlapping interests: This becomes complex since we have to re-run the old notifications for a new
         * subscriber.
         */
        return Observable.error(new UnsupportedOperationException("Upgrade not yet implemented."));
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> asObservable() {
        return interestStream;
    }

    @Override
    public void heartbeat() {
        discoveryClient.heartbeat().doOnError(new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                close(throwable);
            }
        });
    }
}
