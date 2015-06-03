package com.netflix.eureka.cluster;

import com.netflix.discovery.shared.EurekaHttpClient;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.cluster.protocol.ReplicationListResponse;
import com.netflix.eureka.resources.ASGResource.ASGStatus;

/**
 * @author Tomasz Bak
 */
public interface HttpReplicationClient extends EurekaHttpClient {

    HttpResponse<Void> statusUpdate(String asgName, ASGStatus newStatus);

    HttpResponse<ReplicationListResponse> submitBatchUpdates(ReplicationList replicationList);

}
