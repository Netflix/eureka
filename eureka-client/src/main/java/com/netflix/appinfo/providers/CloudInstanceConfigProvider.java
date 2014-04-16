package com.netflix.appinfo.providers;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.netflix.appinfo.CloudInstanceConfig;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaNamespace;

/**
 * This provider is necessary because the namespace is optional
 * @author elandau
 */
@Singleton
public class CloudInstanceConfigProvider implements Provider<CloudInstanceConfig> {
    @Inject(optional=true)
    @EurekaNamespace 
    private String namespace;
    
    @Override
    public CloudInstanceConfig get() {
        CloudInstanceConfig config;
        if (namespace == null)
            config = new CloudInstanceConfig();
        else
            config = new CloudInstanceConfig(namespace);
        
        // TOOD: Remove this when DiscoveryManager is finally no longer used
        DiscoveryManager.getInstance().setEurekaInstanceConfig(config);      
        return config;
    }
    
    @PreDestroy
    public void shutdown() {
        // TOOD: Remove this when DiscoveryManager is finally no longer used
        DiscoveryManager.getInstance().setEurekaInstanceConfig(null);      
    }
}
