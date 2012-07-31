/*
 * InstanceResource.java
 *  
 * $Header: $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.resources;

import java.util.Date;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.ReplicaAwareInstanceRegistry;
import com.netflix.discovery.cluster.ReplicaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource to handle instance level resources
 * 
 * @author gkim
 */
@Produces({"application/xml","application/json"})
public class InstanceResource {
    private final static Logger s_logger = LoggerFactory.getLogger(InstanceResource.class); 
    
    private final static ReplicaAwareInstanceRegistry s_registry =
        ReplicaAwareInstanceRegistry.getInstance();
    
    String id;
    ApplicationResource app;
    
    public InstanceResource(ApplicationResource app, String id) {
        this.id = id;
        this.app = app;
    }

    @GET
    public Response getInstanceInfo() {
        InstanceInfo appInfo = s_registry.getInstanceByAppAndId(app.getName(), id);
        if(appInfo != null){
            if(s_logger.isDebugEnabled()){
                s_logger.info("Found: " + app.getName() + " - " + id);
            }
            return Response.ok(appInfo).build();
        }else {
            s_logger.info("Not Found: " + app.getName() + " - " + id);
            return Response.status(Status.NOT_FOUND).build();
        }
    }

    @PUT
    public Response renewLease(
            @HeaderParam(ReplicaNode.HEADER_REPLICATION) String isReplication,
            @QueryParam("overriddenstatus") String overriddenStatus,
            @QueryParam("status") String status,
            @QueryParam("lastDirtyTimestamp") String lastDirtyTimestamp) {
        long clock = 0;
        
        boolean isFromReplicaNode = "true".equals(isReplication);
        boolean isSuccess = s_registry.renew(app.getName(), id, clock,
                isFromReplicaNode);
        if (overriddenStatus != null) {
            s_registry.storeOverriddenStatusIfRequired(overriddenStatus,
                    InstanceStatus.valueOf(overriddenStatus));
        }
        // Check if we need to sync based on dirty time stamp, the client instanced might have changed some value
        if (isSuccess && (lastDirtyTimestamp != null)) {
            isSuccess = (this.validateDirtyTimestamp(
                    Long.valueOf(lastDirtyTimestamp), isFromReplicaNode));
        }
    
        // If the instance status differs for any reason, request a re-register
        if (isSuccess) {
            isSuccess = !shouldSyncStatus(status, isFromReplicaNode);
        }
    
        if (!isSuccess) {
            s_logger.info("Not Found (Renew): " + app.getName() + " - " + id);
            return Response.status(Status.NOT_FOUND).build();
        }
        if (s_logger.isDebugEnabled()) {
            s_logger.info("Found (Renew): " + app.getName() + " - " + id);
        }
        return Response.ok().build();
    }

    @PUT
    @Path("status")
    public Response statusUpdate(
            @QueryParam("value") String newStatus,
            @HeaderParam(ReplicaNode.HEADER_REPLICATION) String isReplication) {  
        try {
        long clock = 0;
        
        boolean isSuccess= s_registry.statusUpdate(app.getName(), 
                id, 
                InstanceStatus.valueOf(newStatus),
                clock, 
                "true".equals(isReplication));
        
        if(isSuccess){
            s_logger.info("Status updated: " + app.getName() + " - " + id + " - " + newStatus);
            return Response.ok().build();
        }else {
            s_logger.warn("Unable to update status: " + app.getName() + " - " + id + " - " + newStatus);
            return Response.status(Status.NOT_ACCEPTABLE).build();
        }
        }
        catch(Throwable e) {
            s_logger.error("Error updating instance {} for status {}", id, newStatus);
            return Response.serverError().build();
        }
    }
    
    @DELETE
    public Response cancelLease( 
            @HeaderParam(ReplicaNode.HEADER_REPLICATION) String isReplication) {
        long clock = 0;
       
        boolean isSuccess= s_registry.cancel(app.getName(), 
                id,
                clock, 
                "true".equals(isReplication));
        
        if(isSuccess){
            s_logger.info("Found (Cancel): " + app.getName() + " - " + id);
            return Response.ok().build();
        }else {
            s_logger.info("Not Found (Cancel): " + app.getName() + " - " + id);
            return Response.status(Status.NOT_FOUND).build();
        }
    }
    


    private boolean shouldSyncStatus(String status, boolean isReplication) {
        InstanceInfo appInfo = s_registry.getInstanceByAppAndId(app.getName(),
                id);
        InstanceStatus instanceStatusFromRegistry = null;
        if (appInfo != null) {
            instanceStatusFromRegistry = appInfo.getStatus();
        }
        InstanceStatus instanceStatusFromReplica = (status != null ? InstanceStatus
                .valueOf(status) : null);
        if (instanceStatusFromReplica != null) {
            // Do sync up only for replication - because the client could have different state when the server
            // state is updated by an external tool
            if ((!instanceStatusFromRegistry.equals(instanceStatusFromReplica))  && isReplication) {
                Object[] args = {id, instanceStatusFromRegistry.name(),
                        instanceStatusFromReplica.name()};
                s_logger.warn(
                        "The instance status for %s is %s from registry, whereas it is %s from replica. Requesting a re-register from replica.",
                        args);

                return true;
            }
        }
        return false;

    }

    private boolean validateDirtyTimestamp(Long lastDirtyTimestamp,
            boolean isReplication) {
        InstanceInfo appInfo = s_registry.getInstanceByAppAndId(app.getName(),
                id);
        if ((appInfo != null)
                && (lastDirtyTimestamp > appInfo.getLastDirtyTimestamp())) {
            Object[] args = {new Date(appInfo.getLastDirtyTimestamp()), new Date(
                    lastDirtyTimestamp), isReplication};
            s_logger.info(
                    "Time to sync, since the last dirty timestamp differs -"
                            + " Registry : %s Incoming: %s Replication: %s",
                    args);
            return false;
        }
        return true;
    }
 
}
