package com.netflix.eureka2.server.service;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.functions.FuncN;

import java.util.ArrayList;
import java.util.List;

/**
 * @author David Liu
 */
public class SelfInfoResolverChain extends ChainableSelfInfoResolver {

    private final ChainableSelfInfoResolver[] resolvers;

    public SelfInfoResolverChain(ChainableSelfInfoResolver... resolvers) {
        this.resolvers = resolvers;
    }

    @Override
    protected Observable<InstanceInfo.Builder> resolveMutable() {
        List<Observable<InstanceInfo.Builder>> observableList = new ArrayList<>();
        for (ChainableSelfInfoResolver resolver : resolvers) {
            observableList.add(resolver.resolveMutable());
        }

        return Observable.combineLatest(observableList, new FuncN<InstanceInfo.Builder>() {
            @Override
            public InstanceInfo.Builder call(Object... args) {
                InstanceInfo.Builder seed = new InstanceInfo.Builder();
                for (Object obj : args) {
                    InstanceInfo.Builder builder = (InstanceInfo.Builder) obj;
                    seed.withBuilder(new InstanceInfo.Builder().withBuilder(builder));  // clone at each step
                }
                return seed;
            }
        });
    }
}
