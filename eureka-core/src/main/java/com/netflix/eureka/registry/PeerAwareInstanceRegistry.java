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
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.resources.ASGResource;

import java.util.List;

/**
 * @author Tomasz Bak
 */
public interface PeerAwareInstanceRegistry extends InstanceRegistry {

    void init(PeerEurekaNodes peerEurekaNodes) throws Exception;

    /**
     * Populates the registry information from a peer eureka node. This
     * operation fails over to other nodes until the list is exhausted if the
     * communication fails.
     */
    int syncUp();

    /**
     * Checks to see if the registry access is allowed or the server is in a
     * situation where it does not all getting registry information. The server
     * does not return registry information for a period specified in
     * {@link com.netflix.eureka.EurekaServerConfig#getWaitTimeInMsWhenSyncEmpty()}, if it cannot
     * get the registry information from the peer eureka nodes at start up.
     *
     * @return false - if the instances count from a replica transfer returned
     *         zero and if the wait time has not elapsed, otherwise returns true
     */
     boolean shouldAllowAccess(boolean remoteRegionRequired);

     void register(InstanceInfo info, boolean isReplication);

     void statusUpdate(final String asgName, final ASGResource.ASGStatus newStatus, final boolean isReplication);
}
