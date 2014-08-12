package com.netflix.eureka.service;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Index;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;
import rx.functions.Action0;
import rx.subjects.PublishSubject;

/**
 * An implementation of {@link InterestChannel}
 *
 * @author Nitesh Kant
 */
public class InterestChannelImpl extends AbstractChannel implements InterestChannel {

    private PublishSubject<ChangeNotification<InstanceInfo>> source;

    public InterestChannelImpl(Index<InstanceInfo> index) {
        Observable<ChangeNotification<InstanceInfo>> refCountedSource = index.publish().refCount()
                                                                             .doOnCompleted(new Action0() {
                                                                                 @Override
                                                                                 public void call() {
                                                                                     close(); // Close when there are no more subs to the interest.
                                                                                 }
                                                                             });
        this.source = PublishSubject.create();
    }

    @Override
    public Observable<Void> upgrade(Interest<InstanceInfo> newInterest) {
        return Observable.error(new UnsupportedOperationException("Upgrade not yet supported.")); // TODO: Implement upgrade.
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> asObservable() {
        return source;
    }

    @Override
    public void close() {
        source.onCompleted();
    }
}
