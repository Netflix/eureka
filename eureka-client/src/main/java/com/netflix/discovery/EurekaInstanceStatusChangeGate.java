package com.netflix.discovery;

import com.netflix.appinfo.InstanceInfo;

/**
 * A gate can be used to limit InstanceStatus changes.
 */
public interface EurekaInstanceStatusChangeGate {

    /**
     * Return `true` if the transition from the current status to `newStatus` is allowed.
     * @param newStatus The instance's new desired status
     * @return `true` if transition is allowed
     */
    boolean isAllowed(InstanceInfo.InstanceStatus newStatus);

}
