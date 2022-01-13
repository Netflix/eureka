package com.netflix.appinfo.providers;

import javax.inject.Provider;

import com.google.inject.Inject;
import com.netflix.appinfo.CloudInstanceConfig;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaNamespace;

/**
 * This provider is necessary because the namespace is optional.
 * @author elandau
 */
public class CloudInstanceConfigProvider implements Provider<CloudInstanceConfig> {
    @Inject(optional = true)
    @EurekaNamespace
    private String namespace;

    private CloudInstanceConfig config;

    @Override
    public synchronized CloudInstanceConfig get() {
        if (config == null) {
            if (namespace == null) {
                config = new CloudInstanceConfig();
            } else {
                config = new CloudInstanceConfig(namespace);
            }

            // TODO: Remove this when DiscoveryManager is finally no longer used
            DiscoveryManager.getInstance().setEurekaInstanceConfig(config);
        }
        return config;
    }

}
