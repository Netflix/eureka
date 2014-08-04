package com.netflix.eureka.interests;

import rx.Observable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Nitesh Kant
 */
public class IndexRegistry<T> {

    private final ConcurrentHashMap<Interest<T>, Index<T>> interestVsIndex;

    public IndexRegistry() {
        this.interestVsIndex = new ConcurrentHashMap<Interest<T>, Index<T>>();
    }

    public Index<T> forInterest(final Interest<T> interest,
                                final Observable<ChangeNotification<T>> dataSource,
                                final Index.InitStateHolder<T> initStateHolder) {
        Index<T> index = interestVsIndex.get(interest);
        if (null != index) {
            return index;
        } else {
            index = Index.forInterest(interest, dataSource, initStateHolder);
            Index<T> existing = interestVsIndex.putIfAbsent(interest, index);
            if (null != existing) {
                // TODO: Dispose new index.
                return existing;
            } else {
                return index;
            }
        }
    }
}
