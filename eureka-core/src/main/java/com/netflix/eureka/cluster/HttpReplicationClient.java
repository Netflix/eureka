package com.netflix.eureka.cluster;

import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.cluster.protocol.ReplicationListResponse;
import com.netflix.eureka.resources.ASGResource.ASGStatus;

/**
 * @author Tomasz Bak
 */
public interface HttpReplicationClient extends EurekaHttpClient {

    EurekaHttpResponse<Void> statusUpdate(String asgName, ASGStatus newStatus);

    EurekaHttpResponse<ReplicationListResponse> submitBatchUpdates(ReplicationList replicationList);
}
