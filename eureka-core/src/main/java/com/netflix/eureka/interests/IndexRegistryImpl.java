package com.netflix.eureka.interests;

import rx.Observable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Nitesh Kant
 */
public class IndexRegistryImpl<T> implements IndexRegistry<T> {

    protected final ConcurrentHashMap<Interest<T>, Index<T>> interestVsIndex;

    public IndexRegistryImpl() {
        this.interestVsIndex = new ConcurrentHashMap<>();
    }

    @Override
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
                index.onCompleted(); // Shutdown for index.
                return existing;
            } else {
                return index;
            }
        }
    }

    @Override
    public synchronized Observable<Void> shutdown() {
        for (Index<T> index : interestVsIndex.values()) {
            index.onCompleted();
        }

        interestVsIndex.clear();
        return Observable.empty();
    }

    // pretty print for debugging
    @Override
    public String toString() {
        return prettyString();
    }

    private String prettyString() {
        StringBuilder sb = new StringBuilder("IndexRegistryImpl\n");
        for (Map.Entry<Interest<T>, Index<T>> entry : interestVsIndex.entrySet()) {
            sb.append(entry).append("\n");
        }
        return sb.toString();
    }
}
