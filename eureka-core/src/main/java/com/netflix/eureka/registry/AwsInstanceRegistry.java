/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka.registry;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.aws.AwsAsgUtil;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.lease.Lease;
import com.netflix.eureka.resources.ServerCodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Override some methods with aws specific usecases.
 *
 * @author David Liu
 */
@Singleton
public class AwsInstanceRegistry extends PeerAwareInstanceRegistryImpl {
    private static final Logger logger = LoggerFactory.getLogger(AwsInstanceRegistry.class);

    private AwsAsgUtil awsAsgUtil;

    @Inject
    public AwsInstanceRegistry(EurekaServerConfig serverConfig,
                               EurekaClientConfig clientConfig,
                               ServerCodecs serverCodecs,
                               EurekaClient eurekaClient) {
        super(serverConfig, clientConfig, serverCodecs, eurekaClient);
    }

    @Override
    public void init(PeerEurekaNodes peerEurekaNodes) throws Exception {
        super.init(peerEurekaNodes);
        this.awsAsgUtil = new AwsAsgUtil(serverConfig, clientConfig, this);
    }

    public AwsAsgUtil getAwsAsgUtil() {
        return awsAsgUtil;
    }

    /**
     * override base method to add asg lookup
     */
    @Override
    protected InstanceInfo.InstanceStatus getOverriddenInstanceStatus(InstanceInfo r,
                                                                      Lease<InstanceInfo> existingLease,
                                                                      boolean isReplication) {
        // ReplicationInstance is DOWN or STARTING - believe that, but when the instance says UP, question that
        // The client instance sends STARTING or DOWN (because of heartbeat failures), then we accept what
        // the client says. The same is the case with replica as well.
        // The OUT_OF_SERVICE from the client or replica needs to be confirmed as well since the service may be
        // currently in SERVICE
        if (
                (!InstanceInfo.InstanceStatus.UP.equals(r.getStatus()))
                        && (!InstanceInfo.InstanceStatus.OUT_OF_SERVICE.equals(r.getStatus()))) {
            logger.debug("Trusting the instance status {} from replica or instance for instance {}",
                    r.getStatus(), r.getId());
            return r.getStatus();
        }
        // Overrides are the status like OUT_OF_SERVICE and UP set by NAC
        InstanceInfo.InstanceStatus overridden = overriddenInstanceStatusMap.get(r.getId());
        // If there are instance specific overrides, then they win - otherwise the ASG status
        if (overridden != null) {
            logger.debug("The instance specific override for instance {} and the value is {}",
                    r.getId(), overridden.name());
            return overridden;
        }
        // If the ASGName is present- check for its status
        boolean isASGDisabled = false;
        if (r.getASGName() != null) {
            isASGDisabled = !awsAsgUtil.isASGEnabled(r);
            logger.debug("The ASG name is specified {} and the value is {}", r.getASGName(), isASGDisabled);
            if (isASGDisabled) {
                return InstanceInfo.InstanceStatus.OUT_OF_SERVICE;
            } else {
                return InstanceInfo.InstanceStatus.UP;
            }
        }
        // This is for backward compatibility until all applications have ASG names, otherwise while starting up
        // the client status may override status replicated from other servers
        if (!isReplication) {
            InstanceInfo.InstanceStatus existingStatus = null;
            if (existingLease != null) {
                existingStatus = existingLease.getHolder().getStatus();
            }
            // Allow server to have its way when the status is UP or OUT_OF_SERVICE
            if (
                    (existingStatus != null)
                            && (InstanceInfo.InstanceStatus.OUT_OF_SERVICE.equals(existingStatus)
                            || InstanceInfo.InstanceStatus.UP.equals(existingStatus))) {
                logger.debug("There is already an existing lease with status {}  for instance {}",
                        existingLease.getHolder().getStatus().name(),
                        existingLease.getHolder().getId());
                return existingLease.getHolder().getStatus();
            }
        }
        logger.debug("Returning the default instance status {} for instance {}",
                r.getStatus(), r.getId());
        return r.getStatus();
    }
}
