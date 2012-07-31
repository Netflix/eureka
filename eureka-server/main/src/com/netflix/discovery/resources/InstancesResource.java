/*
 * InstancesResource.java
 *  
 * $Header: $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.resources;

import org.slf4j.Logger;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.CurrentRequestVersion;
import com.netflix.discovery.InstanceRegistry;
import com.netflix.discovery.ReplicaAwareInstanceRegistry;
import com.netflix.discovery.Version;
import org.slf4j.LoggerFactory;


/**
 * Root resource
 * 
 * @author gkim
 */
@Produces({"application/xml","application/json"})
@Path("/{version}/instances")
public class InstancesResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstancesResource.class); 

    
   private final InstanceRegistry _registery = 
       ReplicaAwareInstanceRegistry.getInstance();
    
    @GET
    @Path("{id}")
    public Response getById(
            @PathParam("version") String version,
            @PathParam("id") String id){
        CurrentRequestVersion.set(Version.toEnum(version));
        List<InstanceInfo> list =  _registery.getInstancesById(id);
        if(list != null && list.size() > 0){
            return Response.ok(list.get(0)).build();
        }else {
            LOGGER.info("Not Found: " + id);
            return Response.status(Status.NOT_FOUND).build();
        }
    }
}
