package com.netflix.eureka2.server.service.selfinfo;

import com.netflix.eureka2.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregatorImpl;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class StatusInfoResolver extends ChainableSelfInfoResolver {

    public StatusInfoResolver(EurekaHealthStatusAggregatorImpl healthStatusAggregator) {
        super(healthStatusAggregator.healthStatus().map(new Func1<HealthStatusUpdate<EurekaHealthStatusAggregator>, InstanceInfoBuilder>() {
            @Override
            public InstanceInfoBuilder call(HealthStatusUpdate<EurekaHealthStatusAggregator> statusUpdate) {
                return InstanceModel.getDefaultModel().newInstanceInfo().withStatus(statusUpdate.getStatus());
            }
        }));
    }
}
