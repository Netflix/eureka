package com.netflix.eureka2.server.config;

import java.util.HashSet;

import com.netflix.eureka2.registry.dsl.AddressSelector;
import com.netflix.eureka2.registry.datacenter.DataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo;
import rx.Observable;
import rx.functions.Func1;

import java.util.UUID;

/**
 * @author David Liu
 */
public class InstanceInfoFromConfig {

    private final EurekaServerConfig config;

    public InstanceInfoFromConfig(EurekaServerConfig config) {
        this.config = config;
    }

    public Observable<InstanceInfo.Builder> get() {
        return resolveDataCenterInfo()
                .take(1)
                .map(new Func1<DataCenterInfo, InstanceInfo.Builder>() {
                    @Override
                    public InstanceInfo.Builder call(DataCenterInfo dataCenterInfo) {
                        final String instanceId = config.getAppName() + '#' + UUID.randomUUID().toString();

                        String address = AddressSelector.selectBy().publicIp(true).or().any().returnNameOrIp(dataCenterInfo.getAddresses());
                        HashSet<String> healthCheckUrls = new HashSet<String>();
                        healthCheckUrls.add("http://" + address + ':' + config.getWebAdminPort() + "/healthcheck");

                        return new InstanceInfo.Builder()
                                .withId(instanceId)
                                .withApp(config.getAppName())
                                .withVipAddress(config.getVipAddress())
                                .withHealthCheckUrls(healthCheckUrls)
                                .withDataCenterInfo(dataCenterInfo)
                                .withStatus(InstanceInfo.Status.UP);  // for now just register with UP
                    }
                });
    }

    private Observable<? extends DataCenterInfo> resolveDataCenterInfo() {
        return LocalDataCenterInfo.forDataCenterType(config.getMyDataCenterType());
    }
}
