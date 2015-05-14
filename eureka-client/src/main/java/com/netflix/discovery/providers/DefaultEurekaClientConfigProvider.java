package com.netflix.discovery.providers;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.EurekaNamespace;

import javax.inject.Singleton;

/**
 * This provider is necessary because the namespace is optional.
 * @author elandau
 */
@Singleton
public class DefaultEurekaClientConfigProvider implements Provider<EurekaClientConfig> {

    @Inject(optional = true)
    @EurekaNamespace
    private String namespace;

    private DefaultEurekaClientConfig config;
    
    @Override
    public synchronized EurekaClientConfig get() {
        if (config == null) {
            if (namespace == null) {
                config = new DefaultEurekaClientConfig();
            } else {
                config = new DefaultEurekaClientConfig(namespace);
            }
        }
        return config;
    }
}
