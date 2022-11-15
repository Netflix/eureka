package com.netflix.eureka;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.discovery.AbstractDiscoveryClientOptionalArgs;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.Jersey3DiscoveryClientOptionalArgs;
import com.netflix.discovery.shared.transport.jersey.TransportClientFactories;
import com.netflix.discovery.shared.transport.jersey3.Jersey3TransportClientFactories;
import com.netflix.eureka.cluster.Jersey3PeerEurekaNodes;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.resources.ServerCodecs;
import com.netflix.eureka.transport.EurekaServerHttpClientFactory;
import com.netflix.eureka.transport.Jersey3EurekaServerHttpClientFactory;

/**
 * Jersey3 eureka server bootstrapper
 * @author Matt Nelson
 */
public class Jersey3EurekaBootStrap extends EurekaBootStrap {

    // for servlet based deployments
    public Jersey3EurekaBootStrap() {
        super(null);
    }

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
    protected AbstractDiscoveryClientOptionalArgs<?> getDiscoveryClientOptionalArgs() {
        Jersey3DiscoveryClientOptionalArgs jersey3DiscoveryClientOptionalArgs = new Jersey3DiscoveryClientOptionalArgs();
        return jersey3DiscoveryClientOptionalArgs;
    }

    @Override
    protected TransportClientFactories getTransportClientFactories() {
        return Jersey3TransportClientFactories.getInstance();
    }

    @Override
    protected EurekaServerHttpClientFactory getEurekaServerHttpClientFactory() {
        return new Jersey3EurekaServerHttpClientFactory();
    }

}
