/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka.resources;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.eureka.util.StatusInfo;
import com.netflix.eureka.util.StatusInfo.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An utility class for exposing information about peer nodes.
 *
 * @author Karthik Ranganathan, Greg Kim
 */
@Path("/{version}/status")
@Produces({"application/xml", "application/json"})
public class StatusResource {
    private static final Logger logger = LoggerFactory.getLogger(StatusResource.class);
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss Z";

    private final String myAppName;
    private final PeerAwareInstanceRegistry registry;
    private final PeerEurekaNodes peerEurekaNodes;

    @Inject
    StatusResource(EurekaServerContext server) {
        this.myAppName = server.getApplicationInfoManager().getInfo().getAppName();
        this.registry = server.getRegistry();
        this.peerEurekaNodes = server.getPeerEurekaNodes();
    }

    public StatusResource() {
        this(EurekaServerContextHolder.getInstance().getServerContext());
    }

    @GET
    public StatusInfo getStatusInfo() {
        Builder builder = Builder.newBuilder();
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

    public static String getCurrentTimeAsString() {
        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
        return format.format(new Date());
    }
}
