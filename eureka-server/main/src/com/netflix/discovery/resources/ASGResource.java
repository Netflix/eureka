/*
 * AppResource.java
 *
 * $Header: //depot/commonlibraries/platform/cloud/src/com/netflix/appinfo/InstanceInfo.java#2 $
 * $DateTime: 2009/01/29 16:37:50 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.resources;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.netflix.discovery.ReplicaAwareInstanceRegistry;
import com.netflix.discovery.cluster.ReplicaNode;
import com.netflix.discovery.util.AwsAsgUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource for disabling/enabling the  ASG.
 */

@Path("/{version}/asg")
@Produces({ "application/xml", "application/json" })
public class ASGResource {

    private static final Logger logger = LoggerFactory.getLogger(ASGResource.class); 

    public enum ASGStatus {

        ENABLED, DISABLED;

        
        public static ASGStatus toEnum(String s) {
            for (ASGStatus e : ASGStatus.values()) {
                if (e.name().equalsIgnoreCase(s)) {
                    return e;
                }
            }
            throw new RuntimeException("Cannot find ASG enum for the given string "
                    + s);
        }
    }

    @PUT
    @Path("{asgName}/status")
    public Response statusUpdate(@PathParam("asgName") String asgName,
            @QueryParam("value") String newStatus,
            @HeaderParam(ReplicaNode.HEADER_REPLICATION) String isReplication) {
        try {
            logger.info("Trying to update ASG Status for ASG {} to {}", asgName, newStatus);
            ASGStatus asgStatus = ASGStatus.valueOf(newStatus.toUpperCase());
            AwsAsgUtil.getInstance().setStatus(asgName, (ASGStatus.DISABLED.equals(asgStatus) ? false : true));
            ReplicaAwareInstanceRegistry.getInstance().statusUpdate(
                    asgName, asgStatus,
                    Boolean.valueOf(isReplication));
            logger.info("Updated ASG Status for ASG {} to {}", asgName, asgStatus);
           
        } catch (Throwable e) {
            logger.error("Cannot update the status" + newStatus + " for the ASG " + asgName, e
                   );
            return Response.serverError().build();
        }
        return Response.ok().build();
    }

}
