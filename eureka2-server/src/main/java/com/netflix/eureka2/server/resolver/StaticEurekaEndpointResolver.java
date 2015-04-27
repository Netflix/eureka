package com.netflix.eureka2.server.resolver;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class StaticEurekaEndpointResolver implements EurekaEndpointResolver {

    private final Observable<ChangeNotification<EurekaEndpoint>> endpointsObserver;

    public StaticEurekaEndpointResolver(EurekaEndpoint eurekaEndpoint) {
        this.endpointsObserver = Observable.just(new ChangeNotification<EurekaEndpoint>(Kind.Add, eurekaEndpoint));
    }

    public StaticEurekaEndpointResolver(List<EurekaEndpoint> eurekaEndpoints) {
        List<ChangeNotification<EurekaEndpoint>> addNotifications = new ArrayList<>(eurekaEndpoints.size());
        for (EurekaEndpoint endpoint : eurekaEndpoints) {
            addNotifications.add(new ChangeNotification<EurekaEndpoint>(Kind.Add, endpoint));
        }
        addNotifications.add(ChangeNotification.<EurekaEndpoint>bufferSentinel());
        this.endpointsObserver = Observable.from(addNotifications);
    }

    @Override
    public Observable<ChangeNotification<EurekaEndpoint>> eurekaEndpoints() {
        return endpointsObserver;
    }
}
