package com.netflix.eureka2.server.service;

import java.util.HashSet;
import java.util.UUID;

import com.netflix.eureka2.registry.datacenter.DataCenterInfo;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.selector.AddressSelector;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import rx.Observable;
import rx.functions.Func1;

/**
 * A self info resolver that auto resolves datacenter info and then resolves config information into an instance info.
 *
 * @author David Liu
 */
public class ConfigSelfInfoResolver extends ChainableSelfInfoResolver {

    private final String instanceUUID;
    private final EurekaCommonConfig config;

    public ConfigSelfInfoResolver(EurekaCommonConfig config) {
        this.config = config;
        this.instanceUUID = UUID.randomUUID().toString();
    }

    @Override
    protected Observable<InstanceInfo.Builder> resolveMutable() {
        return resolveDataCenterInfo()
                .map(new Func1<DataCenterInfo, InstanceInfo.Builder>() {
                    @Override
                    public InstanceInfo.Builder call(DataCenterInfo dataCenterInfo) {
                        final String instanceId = config.getAppName() + '#' + instanceUUID;

                        String address = AddressSelector.selectBy().publicIp(true).or().any().returnNameOrIp(dataCenterInfo.getAddresses());
                        HashSet<String> healthCheckUrls = new HashSet<>();
                        healthCheckUrls.add("http://" + address + ':' + config.getWebAdminPort() + "/healthcheck");

                        return new InstanceInfo.Builder()
                                .withId(instanceId)
                                .withApp(config.getAppName())
                                .withVipAddress(config.getVipAddress())
                                .withHealthCheckUrls(healthCheckUrls)
                                .withDataCenterInfo(dataCenterInfo)
                                .withStatus(InstanceInfo.Status.STARTING);
                    }
                });
    }

    private Observable<? extends DataCenterInfo> resolveDataCenterInfo() {
        return LocalDataCenterInfo.forDataCenterType(config.getMyDataCenterType());
    }
}
