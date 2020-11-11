package com.netflix.appinfo.providers;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.Ec2EurekaArchaius2InstanceConfig;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.archaius.api.Config;
import com.netflix.discovery.DiscoveryManager;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class CustomAmazonInfoProviderInstanceConfigFactory implements EurekaInstanceConfigFactory {

    private final Config configInstance;
    private final Provider<AmazonInfo> amazonInfoProvider;
    private EurekaInstanceConfig eurekaInstanceConfig;

    @Inject
    public CustomAmazonInfoProviderInstanceConfigFactory(Config configInstance, AmazonInfoProviderFactory amazonInfoProviderFactory) {
        this.configInstance = configInstance;
        this.amazonInfoProvider = amazonInfoProviderFactory.get();
    }

    @Override
    public EurekaInstanceConfig get() {
        if (eurekaInstanceConfig == null) {
            eurekaInstanceConfig = new Ec2EurekaArchaius2InstanceConfig(configInstance, amazonInfoProvider);

            // Copied from CompositeInstanceConfigFactory.get
            DiscoveryManager.getInstance().setEurekaInstanceConfig(eurekaInstanceConfig);
        }

        return eurekaInstanceConfig;
    }
}
