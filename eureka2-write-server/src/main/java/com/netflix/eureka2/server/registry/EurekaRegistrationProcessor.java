package com.netflix.eureka2.server.registry;

import com.netflix.eureka2.EurekaCloseable;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.notification.ChangeNotification;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public interface EurekaRegistrationProcessor<T> extends EurekaCloseable {

    Observable<Void> connect(String id, Source source, Observable<ChangeNotification<T>> registrationUpdates);

    Observable<Integer> sizeObservable();

    int size();
}
