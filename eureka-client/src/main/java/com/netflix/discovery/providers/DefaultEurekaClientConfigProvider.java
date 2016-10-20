package com.netflix.discovery.providers;

import javax.inject.Provider;

import com.google.inject.Inject;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.EurekaNamespace;

/**
 * This provider is necessary because the namespace is optional.
 * @author elandau
 */
public class DefaultEurekaClientConfigProvider implements Provider<EurekaClientConfig> {

    @Inject(optional = true)
    @EurekaNamespace
    private String namespace;

    private DefaultEurekaClientConfig config;
    
    @Override
    public synchronized EurekaClientConfig get() {
        if (config == null) {
            config = (namespace == null)
                    ? new DefaultEurekaClientConfig()
                    : new DefaultEurekaClientConfig(namespace);
                    
            // TODO: Remove this when DiscoveryManager is finally no longer used
            DiscoveryManager.getInstance().setEurekaClientConfig(config);
        }

        return config;
    }
}
