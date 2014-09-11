package com.netflix.eureka.interests;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka.registry.EurekaRegistry;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
public class IndexRegistryImpl<T> implements IndexRegistry<T> {

    final ConcurrentHashMap<Interest<T>, Index<T>> interestVsIndex;

    public IndexRegistryImpl() {
        this.interestVsIndex = new ConcurrentHashMap<Interest<T>, Index<T>>();
    }

    @Override
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

    @Override
    public Observable<ChangeNotification<T>> forCompositeInterest(MultipleInterests<T> interest, EurekaRegistry<T> registry) {
        List<Observable<ChangeNotification<T>>> indexes = new ArrayList<>();
        for (Interest<T> atomicIntrest : interest.flatten()) {
            indexes.add(registry.forInterest(atomicIntrest));
        }
        return Observable.merge(indexes);
    }

    @Override
    public Observable<Void> shutdown() {
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
