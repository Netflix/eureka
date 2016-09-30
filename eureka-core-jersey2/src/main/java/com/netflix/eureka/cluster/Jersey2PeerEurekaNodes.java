package com.netflix.eureka.cluster;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.resources.ServerCodecs;
import com.netflix.eureka.transport.Jersey2ReplicationClient;

/**
 * Jersey2 implementation of PeerEurekaNodes that uses the Jersey2 replication client
 * @author Matt Nelson
 */
public class Jersey2PeerEurekaNodes extends PeerEurekaNodes {

    public Jersey2PeerEurekaNodes(PeerAwareInstanceRegistry registry, EurekaServerConfig serverConfig,
            EurekaClientConfig clientConfig, ServerCodecs serverCodecs, ApplicationInfoManager applicationInfoManager) {
        super(registry, serverConfig, clientConfig, serverCodecs, applicationInfoManager);
    }
    
    @Override
    protected PeerEurekaNode createPeerEurekaNode(String peerEurekaNodeUrl) {
        HttpReplicationClient replicationClient = Jersey2ReplicationClient.createReplicationClient(serverConfig, serverCodecs, peerEurekaNodeUrl);
        String targetHost = hostFromUrl(peerEurekaNodeUrl);
        if (targetHost == null) {
            targetHost = "host";
        }
        return new PeerEurekaNode(registry, targetHost, peerEurekaNodeUrl, replicationClient, serverConfig);
    }
}
