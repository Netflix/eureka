package com.netflix.eureka2.server.service.selfinfo;

import com.netflix.eureka2.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Builder;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregatorImpl;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class StatusInfoResolver extends ChainableSelfInfoResolver {

    public StatusInfoResolver(EurekaHealthStatusAggregatorImpl healthStatusAggregator) {
        super(healthStatusAggregator.healthStatus().map(new Func1<HealthStatusUpdate<EurekaHealthStatusAggregator>, Builder>() {
            @Override
            public Builder call(HealthStatusUpdate<EurekaHealthStatusAggregator> statusUpdate) {
                return new InstanceInfo.Builder().withStatus(statusUpdate.getStatus());
            }
        }));
    }
}
