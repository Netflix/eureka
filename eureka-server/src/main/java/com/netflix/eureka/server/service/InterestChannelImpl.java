package com.netflix.eureka.server.service;

import com.netflix.eureka.datastore.Item;
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
    private final Observable<ChangeNotification<? extends Item>> sourceStream;

    public InterestChannelImpl(Observable<ChangeNotification<? extends Item>> interestStream) {
        super(3, 30000);
        sourceStream = interestStream.lift(
                new Observable.Operator<ChangeNotification<? extends Item>, ChangeNotification<? extends Item>>() {
                    @Override
                    public Subscriber<? super ChangeNotification<? extends Item>> call(
                            Subscriber<? super ChangeNotification<? extends Item>> child) {
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
    public Observable<ChangeNotification<? extends Item>> asObservable() {
        return sourceStream;
    }

    @Override
    public void _close() {
        allSubscriptions.unsubscribe(); // Unsubscribes all the subscriptions.
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        throw new RuntimeException("not implemented yet");
    }
}
