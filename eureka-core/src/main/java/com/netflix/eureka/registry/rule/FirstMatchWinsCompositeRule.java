package com.netflix.eureka.registry.rule;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.lease.Lease;

/**
 * This rule takes an ordered list of rules and returns the result of the first match or the
 * result of the {@link DefaultStatusRule}.
 *
 * Created by Nikos Michalakis on 7/13/16.
 */
public class FirstMatchWinsCompositeRule implements InstanceStatusOverrideRule {

    private InstanceStatusOverrideRule[] rules;
    private InstanceStatusOverrideRule defaultRule;

    public FirstMatchWinsCompositeRule(InstanceStatusOverrideRule... rules) {
        this.rules = rules;
        this.defaultRule = new DefaultStatusRule();
    }

    @Override
    public StatusOverrideResult apply(InstanceInfo instanceInfo,
                                      Lease<InstanceInfo> existingLease,
                                      boolean isReplication) {
        for (int i = 0; i < this.rules.length; ++i) {
            StatusOverrideResult result = this.rules[i].apply(instanceInfo, existingLease, isReplication);
            if (result.matches()) {
                return result;
            }
        }
        return defaultRule.apply(instanceInfo, existingLease, isReplication);
    }
}
