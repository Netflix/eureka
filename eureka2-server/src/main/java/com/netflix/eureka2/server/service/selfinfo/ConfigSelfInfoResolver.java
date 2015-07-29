package com.netflix.eureka2.server.service.selfinfo;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import rx.Observable;

import java.util.UUID;

/**
 * A self info resolver that auto resolves datacenter info and then resolves config information into an instance info.
 *
 * @author David Liu
 */
public class ConfigSelfInfoResolver extends ChainableSelfInfoResolver {

    public ConfigSelfInfoResolver(final EurekaInstanceInfoConfig instanceConfig) {
        super(getFixedSelfInfo(instanceConfig));
    }

    private static Observable<InstanceInfo.Builder> getFixedSelfInfo(EurekaInstanceInfoConfig instanceConfig) {
        String uniqueId = instanceConfig.getUniqueId() == null
                ? UUID.randomUUID().toString()
                : instanceConfig.getUniqueId();

        final String instanceId = instanceConfig.getEurekaApplicationName() + "__" + uniqueId;
        InstanceInfo.Builder builder = new InstanceInfo.Builder()
                .withId(instanceId)
                .withApp(instanceConfig.getEurekaApplicationName())
                .withVipAddress(instanceConfig.getEurekaVipAddress())
                .withStatus(InstanceInfo.Status.STARTING);

        return Observable.just(builder);
    }
}
