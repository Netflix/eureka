package com.netflix.eureka2.registry;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public interface EurekaRegistryView<T> {

    Observable<T> forSnapshot(Interest<T> interest);

    Observable<T> forSnapshot(Interest<T> interest, Source.SourceMatcher sourceMatcher);

    Observable<ChangeNotification<T>> forInterest(Interest<T> interest);

    Observable<ChangeNotification<T>> forInterest(Interest<T> interest, Source.SourceMatcher sourceMatcher);
}
