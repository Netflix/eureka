package com.netflix.eureka2.server.service.selfinfo;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import rx.Observable;
import rx.functions.FuncN;

/**
 * @author David Liu
 */
public class SelfInfoResolverChain extends ChainableSelfInfoResolver {

    public SelfInfoResolverChain(ChainableSelfInfoResolver... resolvers) {
        super(Observable.combineLatest(getObservableList(resolvers), new FuncN<InstanceInfoBuilder>() {
                    @Override
                    public InstanceInfoBuilder call(Object... args) {
                        InstanceInfoBuilder seed = InstanceModel.getDefaultModel().newInstanceInfo();
                        for (Object obj : args) {
                            InstanceInfoBuilder builder = (InstanceInfoBuilder) obj;
                            seed.withBuilder(InstanceModel.getDefaultModel().newInstanceInfo().withBuilder(builder));  // clone at each step
                        }
                        return seed;
                    }
                })
        );
    }

    protected static List<Observable<InstanceInfoBuilder>> getObservableList(ChainableSelfInfoResolver... resolvers) {
        List<Observable<InstanceInfoBuilder>> observableList = new ArrayList<>();
        for (ChainableSelfInfoResolver resolver : resolvers) {
            observableList.add(resolver.resolveMutable());
        }
        return observableList;
    }
}
