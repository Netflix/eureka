/*
 * StatusResource.java
 *  
 * $Header:  $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.resources;


import java.text.SimpleDateFormat;
import java.util.Date;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import com.netflix.discovery.util.StatusInfo;
import com.netflix.discovery.util.StatusInfo.Builder;
import com.netflix.discovery.ReplicaAwareInstanceRegistry;
import com.netflix.discovery.cluster.ReplicaNode;

/**
 * Status resource
 * 
 * @author gkim
 */
@Path("/{version}/status")
@Produces({"application/xml","application/json"})
public class StatusResource {
    //private @PathParam("version") String _apiVersion;
    private final static String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss Z";

    private final static ReplicaAwareInstanceRegistry s_registry = 
        ReplicaAwareInstanceRegistry.getInstance();
    
    @GET
    public StatusInfo getStatusInfo() {
        Builder builder = Builder.newBuilder();
        //Add application level status      
        StringBuilder upReplicas = new StringBuilder();
        StringBuilder downReplicas = new StringBuilder();
        
        StringBuilder replicaHostNames = new StringBuilder();
        for(ReplicaNode node : s_registry.getReplicaNodes()){
            if(replicaHostNames.length() > 0) {
                replicaHostNames.append(", ");
            }
            replicaHostNames.append(node.getServiceUrl());
            if(node.getStatus().isAvailable()){
                upReplicas.append(node.getServiceUrl()).append(',');
            }else {
                downReplicas.append(node.getServiceUrl()).append(',');
            }
        }

        builder.add("registered-replicas", replicaHostNames.toString());
        builder.add("available-replicas", upReplicas.toString());
        builder.add("unavailable-replicas", downReplicas.toString());

        return builder.build();
    }
    
    public static String getCurrentTimeAsString() {
        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
        return format.format(new Date());
    }
}
