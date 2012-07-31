/*
 * ApplicationResource.java
 *  
 * $Header: $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.resources;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.CurrentRequestVersion;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.ReplicaAwareInstanceRegistry;
import com.netflix.discovery.Version;
import com.netflix.discovery.cluster.ReplicaNode;
import com.netflix.discovery.resources.ResponseCache.Key;
import com.netflix.discovery.resources.ResponseCache.KeyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource to handle application level resources
 * 
 * @author gkim
 */
@Produces({"application/xml","application/json"})
public class ApplicationResource {
    private static final Logger s_logger = LoggerFactory.getLogger(ApplicationResource.class); 
    
    private static final ReplicaAwareInstanceRegistry s_registry =
        ReplicaAwareInstanceRegistry.getInstance();
    
    String _appName;
    
    public ApplicationResource(String appName) {
        _appName = appName.toUpperCase();
    }

    @GET
    public Response getApplication(
            @PathParam("version") String version,
            @HeaderParam("Accept") final String acceptHeader) {
        if  (!ReplicaAwareInstanceRegistry.getInstance().shouldAllowAccess()) {
            return Response.status(Status.FORBIDDEN).build();
        }
        CurrentRequestVersion.set(Version.toEnum(version));
        KeyType keyType = KeyType.JSON;
        if(acceptHeader == null || !acceptHeader.contains("json")){
            keyType = KeyType.XML;
        }
        
        Key cacheKey = new Key(_appName, keyType, CurrentRequestVersion.get());
        
        String payLoad = ResponseCache.getInstance().get(cacheKey);

        if(payLoad != null){
            if(s_logger.isDebugEnabled()){
                s_logger.debug("Found: " + _appName);
            }
            return Response.ok(payLoad).build();
        }else {
            s_logger.info("Not Found: " + _appName);
            return Response.status(Status.NOT_FOUND).build();
        }
    }

    @Path("{id}")
    public InstanceResource getInstanceInfo(@PathParam("id") String id) {
        return new InstanceResource(this, id);
    }
    
    @POST
    @Consumes({"application/json","application/xml"})
    public void addInstance(InstanceInfo info, 
            @HeaderParam(ReplicaNode.HEADER_REPLICATION) String isReplication) {
        long clock = 0;
        
        s_registry.register(info, clock, "true".equals(isReplication)); 
    }
    
    String getName() { return _appName; }
    
}