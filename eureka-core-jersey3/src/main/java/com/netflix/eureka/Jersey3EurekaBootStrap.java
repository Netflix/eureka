package com.netflix.eureka;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.eureka.cluster.Jersey3PeerEurekaNodes;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.resources.ServerCodecs;

/**
 * Jersey3 eureka server bootstrapper
 * @author Matt Nelson
 */
public class Jersey3EurekaBootStrap extends EurekaBootStrap {
    
    public Jersey3EurekaBootStrap(DiscoveryClient discoveryClient) {
        super(discoveryClient);
    }

    @Override    
    protected PeerEurekaNodes getPeerEurekaNodes(PeerAwareInstanceRegistry registry, EurekaServerConfig eurekaServerConfig, EurekaClientConfig eurekaClientConfig, ServerCodecs serverCodecs, ApplicationInfoManager applicationInfoManager) {
        PeerEurekaNodes peerEurekaNodes = new Jersey3PeerEurekaNodes(
                registry,
                eurekaServerConfig,
                eurekaClientConfig,
                serverCodecs,
                applicationInfoManager
        );
        
        return peerEurekaNodes;
    }

    @Override
    protected EurekaHttpClient getEurekaHttpClient() {
        // FIXME 2.0
        return null;
    }
}
