package com.netflix.discovery;

import java.util.List;

import com.netflix.appinfo.InstanceInfo;

/**
 * Stupid proxy because I don't want to change the visibility of DiscoveryClient
 * @author elandau
 *
 */
public class DiscoveryClientProxy {
    private DiscoveryClient client;
    
    public DiscoveryClientProxy(InstanceInfo instanceInfo,
            DefaultEurekaClientConfig defaultEurekaClientConfig) {
        client = new DiscoveryClient(instanceInfo, defaultEurekaClientConfig);
    }
    
    public DiscoveryClient getClient() {
        return client;
    }

    public void register() {
        client.register();
    }
    
    public void unregister() {
        client.unregister();
    }

    public List<InstanceInfo> getInstancesByVipAddress(
            String allRegionsVipAddr, boolean b) {
        return client.getInstancesByVipAddress(allRegionsVipAddr, b);
    }

    public List<InstanceInfo> getInstancesByVipAddress(
            String allRegionsVipAddr, boolean b, String region) {
        return client.getInstancesByVipAddress(allRegionsVipAddr, b, region);
    }
    
}
