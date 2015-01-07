package com.netflix.eureka2.server.service;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author David Liu
 */
public abstract class ChainableSelfInfoResolver implements SelfInfoResolver {

    @Override
    public Observable<InstanceInfo> resolve() {
        return resolveMutable().map(new Func1<InstanceInfo.Builder, InstanceInfo>() {
            @Override
            public InstanceInfo call(InstanceInfo.Builder builder) {
                return builder.build();
            }
        });
    }

    protected abstract Observable<InstanceInfo.Builder> resolveMutable();
}
