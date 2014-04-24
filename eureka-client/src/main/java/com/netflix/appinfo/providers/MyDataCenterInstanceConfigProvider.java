package com.netflix.appinfo.providers;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaNamespace;

public class MyDataCenterInstanceConfigProvider implements Provider<MyDataCenterInstanceConfig> {
    @Inject(optional = true)
    @EurekaNamespace
    private String namespace;

    @Override
    public MyDataCenterInstanceConfig get() {
        MyDataCenterInstanceConfig config;
        if (namespace == null) {
            config = new MyDataCenterInstanceConfig();
        } else {
            config = new MyDataCenterInstanceConfig(namespace);
        }

        DiscoveryManager.getInstance().setEurekaInstanceConfig(config);
        return config;
    }
}
