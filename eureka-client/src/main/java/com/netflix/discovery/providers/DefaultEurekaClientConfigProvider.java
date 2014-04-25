package com.netflix.discovery.providers;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.EurekaNamespace;

/**
 * This provider is necessary because the namespace is optional
 * @author elandau
 */
public class DefaultEurekaClientConfigProvider implements Provider<EurekaClientConfig> {

    @Inject(optional=true)
    @EurekaNamespace 
    private String namespace;
    
    @Override
    public EurekaClientConfig get() {
        if (namespace == null)
            return new DefaultEurekaClientConfig();
        else
            return new DefaultEurekaClientConfig(namespace);
    }
}
