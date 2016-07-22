package com.netflix.eureka.registry.rule;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.lease.Lease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * This rule checks to see if we have overrides for an instance and if we do then we return those.
 *
 * Created by Nikos Michalakis on 7/13/16.
 */
public class OverrideExistsRule implements InstanceStatusOverrideRule {

    private static final Logger logger = LoggerFactory.getLogger(OverrideExistsRule.class);

    private Map<String, InstanceInfo.InstanceStatus> statusOverrides;

    public OverrideExistsRule(Map<String, InstanceInfo.InstanceStatus> statusOverrides) {
        this.statusOverrides = statusOverrides;
    }

    @Override
    public StatusOverrideResult apply(InstanceInfo instanceInfo, Lease<InstanceInfo> existingLease, boolean isReplication) {
        InstanceInfo.InstanceStatus overridden = statusOverrides.get(instanceInfo.getId());
        // If there are instance specific overrides, then they win - otherwise the ASG status
        if (overridden != null) {
            logger.debug("The instance specific override for instance {} and the value is {}",
                    instanceInfo.getId(), overridden.name());
            return StatusOverrideResult.matchingStatus(overridden);
        }
        return StatusOverrideResult.NO_MATCH;
    }

    @Override
    public String toString() {
        return OverrideExistsRule.class.getName();
    }

}
