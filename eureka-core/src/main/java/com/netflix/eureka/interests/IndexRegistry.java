package com.netflix.eureka.interests;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka.registry.EurekaRegistry;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
public class IndexRegistry<T> {

    private final ConcurrentHashMap<Interest<T>, Index<T>> interestVsIndex;

    public IndexRegistry() {
        this.interestVsIndex = new ConcurrentHashMap<Interest<T>, Index<T>>();
    }

    public Observable<ChangeNotification<T>> forInterest(final Interest<T> interest,
                                                         final Observable<ChangeNotification<T>> dataSource,
                                                         final Index.InitStateHolder<T> initStateHolder) {
        Index<T> index = interestVsIndex.get(interest);
        if (null != index) {
            return index;
        } else {
            index = Index.forInterest(interest, dataSource, initStateHolder);
            Index<T> existing = interestVsIndex.putIfAbsent(interest, index);
            if (null != existing) {
                index.onCompleted(); // Shutdown for index.
                return existing;
            } else {
                return index;
            }
        }
    }

    public Observable<ChangeNotification<T>> forCompositeInterest(MultipleInterests<T> interest, EurekaRegistry<T> registry) {
        List<Observable<ChangeNotification<T>>> indexes = new ArrayList<>();
        for (Interest<T> atomicIntrest : interest.flatten()) {
            indexes.add(registry.forInterest(atomicIntrest));
        }
        return Observable.merge(indexes);
    }

    public Observable<Void> shutdown() {
        for (Index<T> index : interestVsIndex.values()) {
            index.onCompleted();
        }
        return Observable.empty();
    }
}
