package com.netflix.eureka.interests;

import rx.Observable;

/**
 * @author David Liu
 */
public interface IndexRegistry<T> {
    Index<T> forInterest(final Interest<T> interest,
                                final Observable<ChangeNotification<T>> dataSource,
                                final Index.InitStateHolder<T> initStateHolder);

    Observable<Void> completeInterest(Interest<T> interest);

    Observable<Void> errorInterest(Interest<T> interest, Throwable e);

    Observable<Void> shutdown();
}
