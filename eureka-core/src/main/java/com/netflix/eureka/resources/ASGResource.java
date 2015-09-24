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
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.eureka.aws.AwsAsgUtil;
import com.netflix.eureka.registry.AwsInstanceRegistry;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <em>jersey</em> resource for handling updates to {@link ASGStatus}.
 *
 * <p>
 * The ASG status is used in <em>AWS</em> environments to automatically
 * enable/disable instance registration based on the status of the ASG. This is
 * particularly useful in <em>red/black</em> deployment scenarios where it is
 * easy to switch to a new version and incase of problems switch back to the old
 * versions of the deployment.
 * </p>
 *
 * <p>
 * During such a scenario, when an ASG is disabled and the instances go away and
 * get refilled by an ASG - which is normal in AWS environments,the instances
 * automatically go in the {@link com.netflix.appinfo.InstanceInfo.InstanceStatus#OUT_OF_SERVICE} state when they
 * are refilled by the ASG and if the ASG is disabled by as indicated by a flag
 * in the ASG as described in {@link AwsAsgUtil#isASGEnabled}
 * </p>
 *
 * @author Karthik Ranganathan
 *
 */
@Path("/{version}/asg")
@Produces({"application/xml", "application/json"})
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
            throw new RuntimeException("Cannot find ASG enum for the given string " + s);
        }
    }

    protected final PeerAwareInstanceRegistry registry;
    protected final AwsAsgUtil awsAsgUtil;

    @Inject
    ASGResource(EurekaServerContext eurekaServer) {
        this.registry = eurekaServer.getRegistry();
        if (registry instanceof AwsInstanceRegistry) {
            this.awsAsgUtil = ((AwsInstanceRegistry) registry).getAwsAsgUtil();
        } else {
            this.awsAsgUtil = null;
        }
    }

    public ASGResource() {
        this(EurekaServerContextHolder.getInstance().getServerContext());
    }

    /**
     * Changes the status information of the ASG.
     *
     * @param asgName the name of the ASG for which the status needs to be changed.
     * @param newStatus the new status {@link ASGStatus} of the ASG.
     * @param isReplication a header parameter containing information whether this is replicated from other nodes.
     *
     * @return response which indicates if the operation succeeded or not.
     */
    @PUT
    @Path("{asgName}/status")
    public Response statusUpdate(@PathParam("asgName") String asgName,
                                 @QueryParam("value") String newStatus,
                                 @HeaderParam(PeerEurekaNode.HEADER_REPLICATION) String isReplication) {
        if (awsAsgUtil == null) {
            return Response.status(400).build();
        }

        try {
            logger.info("Trying to update ASG Status for ASG {} to {}", asgName, newStatus);
            ASGStatus asgStatus = ASGStatus.valueOf(newStatus.toUpperCase());
            awsAsgUtil.setStatus(asgName, (!ASGStatus.DISABLED.equals(asgStatus)));
            registry.statusUpdate(asgName, asgStatus, Boolean.valueOf(isReplication));
            logger.debug("Updated ASG Status for ASG {} to {}", asgName, asgStatus);

        } catch (Throwable e) {
            logger.error("Cannot update the status {} for the ASG {}", newStatus, asgName, e);
            return Response.serverError().build();
        }
        return Response.ok().build();
    }

}
