package com.netflix.eureka2.server.service.selfinfo;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author David Liu
 */
public class ChainableSelfInfoResolver implements SelfInfoResolver {

    private final Observable<InstanceInfoBuilder> resultObservable;

    public ChainableSelfInfoResolver(Observable<InstanceInfoBuilder> resolverObservable) {
        resultObservable = resolverObservable;
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        return resolveMutable().map(new Func1<InstanceInfoBuilder, InstanceInfo>() {
            @Override
            public InstanceInfo call(InstanceInfoBuilder builder) {
                return builder.build();
            }
        });
    }

    protected Observable<InstanceInfoBuilder> resolveMutable() {
        return resultObservable.share();
    }
}
