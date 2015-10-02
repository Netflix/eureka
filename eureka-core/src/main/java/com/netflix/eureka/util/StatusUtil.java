package com.netflix.eureka.util;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * @author David Liu
 */
public class StatusUtil {
    private static final Logger logger = LoggerFactory.getLogger(StatusUtil.class);

    private final String myAppName;
    private final PeerAwareInstanceRegistry registry;
    private final PeerEurekaNodes peerEurekaNodes;

    public StatusUtil(EurekaServerContext server) {
        this.myAppName = server.getApplicationInfoManager().getInfo().getAppName();
        this.registry = server.getRegistry();
        this.peerEurekaNodes = server.getPeerEurekaNodes();
    }

    public StatusInfo getStatusInfo() {
        StatusInfo.Builder builder = StatusInfo.Builder.newBuilder();
        // Add application level status
        StringBuilder upReplicas = new StringBuilder();
        StringBuilder downReplicas = new StringBuilder();

        StringBuilder replicaHostNames = new StringBuilder();

        for (PeerEurekaNode node : peerEurekaNodes.getPeerEurekaNodes()) {
            if (replicaHostNames.length() > 0) {
                replicaHostNames.append(", ");
            }
            replicaHostNames.append(node.getServiceUrl());
            if (isReplicaAvailable(myAppName, node.getServiceUrl())) {
                upReplicas.append(node.getServiceUrl()).append(',');
            } else {
                downReplicas.append(node.getServiceUrl()).append(',');
            }
        }

        builder.add("registered-replicas", replicaHostNames.toString());
        builder.add("available-replicas", upReplicas.toString());
        builder.add("unavailable-replicas", downReplicas.toString());

        return builder.build();
    }

    private boolean isReplicaAvailable(String myAppName, String url) {

        try {
            String givenHostName = new URI(url).getHost();
            Application app = registry.getApplication(myAppName, false);
            if (app == null) {
                return false;
            }
            for (InstanceInfo info : app.getInstances()) {
                if (info.getHostName().equals(givenHostName)) {
                    return true;
                }
            }
            givenHostName = new URI(url).getHost();
        } catch (Throwable e) {
            logger.error("Could not determine if the replica is available ", e);
        }
        return false;
    }
}
