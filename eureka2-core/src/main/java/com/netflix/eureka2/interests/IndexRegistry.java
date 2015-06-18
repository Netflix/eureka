package com.netflix.eureka2.interests;

import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

import java.util.Iterator;

/**
 * @author Nitesh Kant
 */
public interface IndexRegistry<T> {

    /**
     * The interest for this call is required to be an atomic interest and not a {@link MultipleInterests}
     */
    Observable<ChangeNotification<T>> forInterest(Interest<T> interest,
                                                  Observable<ChangeNotification<T>> dataSource,
                                                  Index.InitStateHolder<T> initStateHolder);

    Observable<ChangeNotification<T>> forCompositeInterest(MultipleInterests<T> interest, SourcedEurekaRegistry<T> registry);

    Observable<Void> shutdown();

    Observable<Void> shutdown(Throwable cause);
}
