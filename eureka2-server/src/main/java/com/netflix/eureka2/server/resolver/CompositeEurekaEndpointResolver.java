package com.netflix.eureka2.server.resolver;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.interests.ChangeNotification;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class CompositeEurekaEndpointResolver implements EurekaEndpointResolver {

    private final Observable<ChangeNotification<EurekaEndpoint>> mergedResolverObservable;

    public CompositeEurekaEndpointResolver(List<EurekaEndpointResolver> resolvers) {
        List<Observable<ChangeNotification<EurekaEndpoint>>> mergedResolverList = new ArrayList<>(resolvers.size());
        for (EurekaEndpointResolver resolver : resolvers) {
            mergedResolverList.add(resolver.eurekaEndpoints());
        }
        mergedResolverObservable = Observable.merge(mergedResolverList);
    }

    @Override
    public Observable<ChangeNotification<EurekaEndpoint>> eurekaEndpoints() {
        return mergedResolverObservable;
    }
}
