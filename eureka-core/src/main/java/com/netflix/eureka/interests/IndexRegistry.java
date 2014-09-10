package com.netflix.eureka.interests;

import com.netflix.eureka.registry.EurekaRegistry;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
public interface IndexRegistry<T> {

    Observable<ChangeNotification<T>> forInterest(Interest<T> interest,
                                                  Observable<ChangeNotification<T>> dataSource,
                                                  Index.InitStateHolder<T> initStateHolder);

    Observable<ChangeNotification<T>> forCompositeInterest(MultipleInterests<T> interest, EurekaRegistry<T> registry);

    Observable<Void> shutdown();
}
