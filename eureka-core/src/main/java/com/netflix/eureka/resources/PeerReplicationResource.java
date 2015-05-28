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

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.cluster.protocol.ReplicationInstance;
import com.netflix.eureka.cluster.protocol.ReplicationInstanceResponse;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.cluster.protocol.ReplicationListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <em>jersey</em> resource that handles requests for replication purposes.
 *
 * @author Karthik Ranganathan
 *
 */
@Path("/{version}/peerreplication")
@Produces({"application/xml", "application/json"})
public class PeerReplicationResource {
    private static final String REPLICATION = "true";
    private static final Logger logger = LoggerFactory.getLogger(PeerReplicationResource.class);


    /**
     * Process batched replication events from peer eureka nodes.
     *
     * <p>
     *  The batched events are delegated to underlying resources to generate a
     *  {@link ReplicationListResponse} containing the individual responses to the batched events
     * </p>
     *
     * @param replicationList
     *            The List of replication events from peer eureka nodes
     * @return A batched response containing the information about the responses of individual events
     */
    @Path("batch")
    @POST
    public Response batchReplication(
            ReplicationList replicationList) {
        Response response = null;
        try {

            ReplicationListResponse batchResponse = new ReplicationListResponse();
            for (ReplicationInstance instanceInfo : replicationList
                    .getReplicationList()) {
                ApplicationResource applicationResource = new ApplicationResource(
                        instanceInfo.getAppName());
                InstanceResource resource = new InstanceResource(
                        applicationResource, instanceInfo.getId());
                String lastDirtyTimestamp = (instanceInfo
                        .getLastDirtyTimestamp() == null ? null : instanceInfo
                        .getLastDirtyTimestamp().toString());
                String overriddenStatus = (instanceInfo.getOverriddenStatus() == null ? null
                        : instanceInfo.getOverriddenStatus());
                String instanceStatus = (instanceInfo.getStatus() == null ? null
                        : instanceInfo.getStatus());
                ReplicationInstanceResponse.Builder singleResponseBuilder =
                        new ReplicationInstanceResponse.Builder();
                if (instanceInfo.getAction() == Action.Heartbeat) {
                    response = resource.renewLease(REPLICATION, overriddenStatus,
                            instanceStatus, lastDirtyTimestamp);

                    singleResponseBuilder.setStatusCode(response.getStatus());
                    if (response.getStatus() == Response.Status.OK
                            .getStatusCode() && response.getEntity() != null) {
                        singleResponseBuilder
                                .setResponseEntity((InstanceInfo) response
                                        .getEntity());
                    }
                } else if (instanceInfo.getAction() == Action.Register) {
                    applicationResource.addInstance(
                            instanceInfo.getInstanceInfo(), REPLICATION);

                    singleResponseBuilder = new ReplicationInstanceResponse.Builder()
                            .setStatusCode(Status.OK.getStatusCode());
                } else if (instanceInfo.getAction() == Action.StatusUpdate) {
                    response = resource.statusUpdate(instanceInfo.getStatus(),
                            REPLICATION, instanceInfo.getLastDirtyTimestamp()
                                    .toString());

                    singleResponseBuilder = new ReplicationInstanceResponse.Builder()
                            .setStatusCode(response.getStatus());
                } else if (instanceInfo.getAction() == Action.DeleteStatusOverride) {
                    response = resource.deleteStatusUpdate(REPLICATION, instanceInfo.getStatus(),
                            instanceInfo.getLastDirtyTimestamp().toString());

                    singleResponseBuilder = new ReplicationInstanceResponse.Builder()
                            .setStatusCode(response.getStatus());
                } else if (instanceInfo.getAction() == Action.Cancel) {
                    response = resource.cancelLease(REPLICATION);

                    singleResponseBuilder = new ReplicationInstanceResponse.Builder()
                            .setStatusCode(response.getStatus());
                }

                batchResponse.addResponse(singleResponseBuilder.build());
            }
            return Response.ok(batchResponse).build();
        } catch (Throwable e) {
            logger.error("Cannot execute batch Request", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();

        }

    }
}
