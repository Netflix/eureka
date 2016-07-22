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

import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.aws.AwsAsgUtil;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.registry.rule.AsgEnabledRule;
import com.netflix.eureka.registry.rule.DownOrStartingRule;
import com.netflix.eureka.registry.rule.FirstMatchWinsCompositeRule;
import com.netflix.eureka.registry.rule.InstanceStatusOverrideRule;
import com.netflix.eureka.registry.rule.LeaseExistsRule;
import com.netflix.eureka.registry.rule.OverrideExistsRule;
import com.netflix.eureka.resources.ServerCodecs;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Override some methods with aws specific use cases.
 *
 * @author David Liu
 */
@Singleton
public class AwsInstanceRegistry extends PeerAwareInstanceRegistryImpl {

    private AwsAsgUtil awsAsgUtil;

    private InstanceStatusOverrideRule instanceStatusOverrideRule;

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
        // We first check if the instance is STARTING or DOWN, then we check explicit overrides,
        // then we see if our ASG is UP, then we check the status of a potentially existing lease.
        this.instanceStatusOverrideRule = new FirstMatchWinsCompositeRule(new DownOrStartingRule(),
                new OverrideExistsRule(overriddenInstanceStatusMap), new AsgEnabledRule(this.awsAsgUtil),
                new LeaseExistsRule());
    }

    @Override
    protected InstanceStatusOverrideRule getInstanceInfoOverrideRule() {
        return this.instanceStatusOverrideRule;
    }

    public AwsAsgUtil getAwsAsgUtil() {
        return awsAsgUtil;
    }
}
