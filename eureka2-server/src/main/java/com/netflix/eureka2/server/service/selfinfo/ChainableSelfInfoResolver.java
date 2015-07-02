package com.netflix.eureka2.server.service.selfinfo;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author David Liu
 */
public class ChainableSelfInfoResolver implements SelfInfoResolver {

    private final Observable<InstanceInfo.Builder> resultObservable;

    public ChainableSelfInfoResolver(Observable<InstanceInfo.Builder> resolverObservable) {
        resultObservable = resolverObservable;
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        return resolveMutable().map(new Func1<InstanceInfo.Builder, InstanceInfo>() {
            @Override
            public InstanceInfo call(InstanceInfo.Builder builder) {
                return builder.build();
            }
        });
    }

    protected Observable<InstanceInfo.Builder> resolveMutable() {
        return resultObservable.share();
    }
}
