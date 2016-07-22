package com.netflix.eureka.registry.rule;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.lease.Lease;
import com.netflix.eureka.registry.AbstractInstanceRegistry;

/**
 * A single rule that if matched it returns an instance status.
 * The idea is to use an ordered list of such rules and pick the first result that matches.
 *
 * It is designed to be used by
 * {@link AbstractInstanceRegistry#getOverriddenInstanceStatus(InstanceInfo, Lease, boolean)}
 *
 * Created by Nikos Michalakis on 7/13/16.
 */
public interface InstanceStatusOverrideRule {

    /**
     * Match this rule.
     *
     * @param instanceInfo The instance info whose status we care about.
     * @param existingLease Does the instance have an existing lease already? If so let's consider that.
     * @param isReplication When overriding consider if we are under a replication mode from other servers.
     * @return A result with whether we matched and what we propose the status to be overriden to.
     */
    StatusOverrideResult apply(final InstanceInfo instanceInfo,
                               final Lease<InstanceInfo> existingLease,
                               boolean isReplication);

}
