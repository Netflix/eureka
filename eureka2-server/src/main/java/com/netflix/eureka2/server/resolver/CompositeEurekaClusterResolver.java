package com.netflix.eureka2.server.resolver;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.interests.ChangeNotification;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class CompositeEurekaClusterResolver implements EurekaClusterResolver {

    private final Observable<ChangeNotification<ClusterAddress>> mergedResolverObservable;

    public CompositeEurekaClusterResolver(List<EurekaClusterResolver> resolvers) {
        List<Observable<ChangeNotification<ClusterAddress>>> mergedResolverList = new ArrayList<>(resolvers.size());
        for (EurekaClusterResolver resolver : resolvers) {
            mergedResolverList.add(resolver.clusterTopologyChanges());
        }
        mergedResolverObservable = Observable.merge(mergedResolverList);
    }

    @Override
    public Observable<ChangeNotification<ClusterAddress>> clusterTopologyChanges() {
        return mergedResolverObservable;
    }
}
