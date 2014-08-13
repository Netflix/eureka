package com.netflix.eureka.server.service;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import rx.Observable;
import rx.Subscriber;
import rx.subscriptions.CompositeSubscription;

/**
 * An implementation of {@link com.netflix.eureka.service.InterestChannel}
 *
 * @author Nitesh Kant
 */
public class InterestChannelImpl extends AbstractChannel implements InterestChannel {

    private final CompositeSubscription allSubscriptions;
    private final Observable<ChangeNotification<InstanceInfo>> sourceStream;

    public InterestChannelImpl(Observable<ChangeNotification<InstanceInfo>> interestStream) {
        super(3, 30000);
        sourceStream = interestStream.lift(
                new Observable.Operator<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
                    @Override
                    public Subscriber<? super ChangeNotification<InstanceInfo>> call(
                            Subscriber<? super ChangeNotification<InstanceInfo>> child) {
                        allSubscriptions.add(child); // Adds all subscriptions so they can be unsubscribed on close()
                        return child;
                    }
                });
        allSubscriptions = new CompositeSubscription();
    }

    @Override
    public Observable<Void> upgrade(Interest<InstanceInfo> newInterest) {
        return Observable.error(new UnsupportedOperationException("Upgrade not yet supported.")); // TODO: Implement upgrade.
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> asObservable() {
        return sourceStream;
    }

    @Override
    public void _close() {
        allSubscriptions.unsubscribe(); // Unsubscribes all the subscriptions.
    }
}
