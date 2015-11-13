package com.netflix.eureka2.client.interest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.netflix.eureka2.model.InterestModel;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.MultipleInterests;
import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

/**
 * A class that tracks and refCounts current interests for EurekaInterestClient.
 * This class needs to support concurrent access.
 * TODO rx-y serialization without the synchronized. This is a low volume class not on the critical path, do we care enough?
 *
 * @author David Liu
 */
public class InterestTracker {
    private final ConcurrentMap<Interest<InstanceInfo>, Integer> interests = new ConcurrentHashMap<>();
    private volatile MultipleInterests<InstanceInfo> multipleInterests = InterestModel.getDefaultModel().newMultipleInterests();

    private final Subject<Interest<InstanceInfo>, Interest<InstanceInfo>> interestSubject = BehaviorSubject.create();

    public synchronized void appendInterest(final Interest<InstanceInfo> interest) {
        Integer refCount = interests.putIfAbsent(interest, 1);
        if (refCount != null) {
            interests.put(interest, refCount + 1);
        } else {
            multipleInterests = multipleInterests.copyAndAppend(interest);
            interestSubject.onNext(multipleInterests);
        }
    }

    public synchronized void removeInterest(final Interest<InstanceInfo> interest) {
        Integer refCount = interests.get(interest);
        if (refCount != null) {
            if (refCount <= 1) {
                interests.remove(interest);
                multipleInterests = multipleInterests.copyAndRemove(interest);
                interestSubject.onNext(multipleInterests);
            } else {
                interests.put(interest, refCount - 1);
            }
        }
    }

    public Observable<Interest<InstanceInfo>> interestChangeStream() {
        return interestSubject.asObservable().distinctUntilChanged();
    }

    public void close() {
        interests.clear();
        interestSubject.onCompleted();
    }
}
