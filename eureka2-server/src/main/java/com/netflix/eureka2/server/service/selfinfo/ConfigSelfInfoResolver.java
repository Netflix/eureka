package com.netflix.eureka2.server.service.selfinfo;

import java.util.UUID;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import rx.Observable;

/**
 * A self info resolver that auto resolves datacenter info and then resolves config information into an instance info.
 *
 * @author David Liu
 */
public class ConfigSelfInfoResolver extends ChainableSelfInfoResolver {

    public ConfigSelfInfoResolver(final EurekaInstanceInfoConfig instanceConfig) {
        super(getFixedSelfInfo(instanceConfig));
    }

    public static Observable<InstanceInfoBuilder> getFixedSelfInfo(EurekaInstanceInfoConfig instanceConfig) {
        String uniqueId = instanceConfig.getUniqueId() == null
                ? UUID.randomUUID().toString()
                : instanceConfig.getUniqueId();

        final String instanceId = instanceConfig.getEurekaApplicationName() + "__" + uniqueId;
        InstanceInfoBuilder builder = InstanceModel.getDefaultModel().newInstanceInfo()
                .withId(instanceId)
                .withApp(instanceConfig.getEurekaApplicationName())
                .withVipAddress(instanceConfig.getEurekaVipAddress())
                .withStatus(InstanceInfo.Status.STARTING);

        return Observable.just(builder);
    }
}
