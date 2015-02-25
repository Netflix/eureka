package com.netflix.eureka2.server.service;

import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregator;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class StatusInfoResolver extends ChainableSelfInfoResolver {

    public StatusInfoResolver(EurekaHealthStatusAggregator healthStatusAggregator) {
        super(healthStatusAggregator.healthStatus().map(new Func1<HealthStatusUpdate<Status, EurekaHealthStatusAggregator>, Builder>() {
            @Override
            public Builder call(HealthStatusUpdate<Status, EurekaHealthStatusAggregator> statusUpdate) {
                return new InstanceInfo.Builder()
                        .withStatus(statusUpdate.getEurekaStatus());
            }
        }));
    }
}
