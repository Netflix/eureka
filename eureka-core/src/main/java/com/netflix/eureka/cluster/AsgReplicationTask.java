package com.netflix.eureka.cluster;

import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.resources.ASGResource.ASGStatus;

/**
 * Base {@link ReplicationTask} class for ASG related replication requests.
 *
 * @author Tomasz Bak
 */
public abstract class AsgReplicationTask extends ReplicationTask {

    private final String asgName;
    private final ASGStatus newStatus;

    protected AsgReplicationTask(String peerNodeName, Action action, String asgName, ASGStatus newStatus) {
        super(peerNodeName, action);
        this.asgName = asgName;
        this.newStatus = newStatus;
    }

    @Override
    public String getTaskName() {
        return asgName + ':' + action + '@' + peerNodeName;
    }

    public String getAsgName() {
        return asgName;
    }

    public ASGStatus getNewStatus() {
        return newStatus;
    }
}
