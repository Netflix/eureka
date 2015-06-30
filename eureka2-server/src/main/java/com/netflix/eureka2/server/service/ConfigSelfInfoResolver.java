package com.netflix.eureka2.server.service;

import java.util.HashSet;
import java.util.UUID;

import com.netflix.eureka2.registry.datacenter.DataCenterInfo;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.selector.AddressSelector;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import rx.functions.Func1;

/**
 * A self info resolver that auto resolves datacenter info and then resolves config information into an instance info.
 *
 * @author David Liu
 */
public class ConfigSelfInfoResolver extends ChainableSelfInfoResolver {

    public ConfigSelfInfoResolver(final EurekaInstanceInfoConfig instanceConfig, final EurekaServerTransportConfig transportConfig) {
        super(LocalDataCenterInfo.forDataCenterType(instanceConfig.getDataCenterType())
                        .map(new Func1<DataCenterInfo, InstanceInfo.Builder>() {
                            @Override
                            public InstanceInfo.Builder call(DataCenterInfo dataCenterInfo) {
                                final String instanceId = instanceConfig.getEurekaApplicationName() + '#' + UUID.randomUUID().toString();
                                ;

                                String address = AddressSelector.selectBy().publicIp(true).or().any().returnNameOrIp(dataCenterInfo.getAddresses());
                                HashSet<String> healthCheckUrls = new HashSet<>();
                                healthCheckUrls.add("http://" + address + ':' + transportConfig.getWebAdminPort() + "/healthcheck");

                                return new InstanceInfo.Builder()
                                        .withId(instanceId)
                                        .withApp(instanceConfig.getEurekaApplicationName())
                                        .withVipAddress(instanceConfig.getEurekaVipAddress())
                                        .withHealthCheckUrls(healthCheckUrls)
                                        .withDataCenterInfo(dataCenterInfo)
                                        .withStatus(InstanceInfo.Status.STARTING);
                            }
                        })
        );
    }
}
