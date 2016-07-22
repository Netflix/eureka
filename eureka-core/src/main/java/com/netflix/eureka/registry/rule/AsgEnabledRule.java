package com.netflix.eureka.registry.rule;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.eureka.aws.AwsAsgUtil;
import com.netflix.eureka.lease.Lease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a rule that checks if the ASG for an instance is enabled or not and if not then it brings the instance
 * OUT_OF_SERVICE.
 *
 * Created by Nikos Michalakis on 7/14/16.
 */
public class AsgEnabledRule implements InstanceStatusOverrideRule {
    private static final Logger logger = LoggerFactory.getLogger(AsgEnabledRule.class);

    private final AwsAsgUtil awsAsgUtil;

    public AsgEnabledRule(AwsAsgUtil awsAsgUtil) {
        this.awsAsgUtil = awsAsgUtil;
    }

    @Override
    public StatusOverrideResult apply(InstanceInfo instanceInfo, Lease<InstanceInfo> existingLease, boolean isReplication) {
        // If the ASGName is present- check for its status
        if (instanceInfo.getASGName() != null) {
            boolean isASGDisabled = !awsAsgUtil.isASGEnabled(instanceInfo);
            logger.debug("The ASG name is specified {} and the value is {}", instanceInfo.getASGName(), isASGDisabled);
            if (isASGDisabled) {
                return StatusOverrideResult.matchingStatus(InstanceStatus.OUT_OF_SERVICE);
            } else {
                return StatusOverrideResult.matchingStatus(InstanceStatus.UP);
            }
        }
        return StatusOverrideResult.NO_MATCH;
    }

    @Override
    public String toString() {
        return AsgEnabledRule.class.getName();
    }
}
