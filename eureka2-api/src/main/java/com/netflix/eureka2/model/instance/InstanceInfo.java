/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.model.instance;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.netflix.eureka2.internal.util.InstanceUtil;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.datacenter.DataCenterInfo;

/**
 */
public interface InstanceInfo {

    enum Status {
        UP,             // Ready for traffic
        DOWN,           // Not ready for traffic - healthcheck failure
        STARTING,       // Not ready for traffic - still initialising
        OUT_OF_SERVICE, // Not ready for traffic - user initiated operation
        UNKNOWN;

        public static Status toEnum(String s) {
            for (Status e : Status.values()) {
                if (e.name().equalsIgnoreCase(s)) {
                    return e;
                }
            }
            return UNKNOWN;
        }
    }

    String getId();

    /**
     * @return the appgroup this instance belong to
     */
    String getAppGroup();

    /**
     * @return the application this instance belong to
     */
    String getApp();

    /**
     * @return the asg this instance belong to
     */
    String getAsg();

    /**
     * @return the vip addresses of this instance
     */
    String getVipAddress();

    /**
     * @return the secure vip address of this instance
     */
    String getSecureVipAddress();

    /**
     * @return the port numbers that is used for servicing requests
     */
    Set<ServicePort> getPorts();

    /**
     * @return the current status of this instance
     */
    Status getStatus();

    /**
     * @return home page {@link java.net.URL}
     */
    String getHomePageUrl();

    /**
     * @return status page {@link java.net.URL}
     */
    String getStatusPageUrl();

    /**
     * Gets the absolute URLs for the health check page for both secure and
     * non-secure protocols. If the port is not enabled then the URL is
     * excluded.
     *
     * @return A Set containing the string representation of health check urls
     *         for secure and non secure protocols
     */
    HashSet<String> getHealthCheckUrls();

    DataCenterInfo getDataCenterInfo();

    Map<String, String> getMetaData();

    Iterator<ServiceEndpoint> serviceEndpoints();

    /**
     * Apply the delta instance to the current InstanceInfo
     *
     * @param delta the delta changes to applyTo
     * @return a new InstanceInfo with the deltas applied
     */
    default InstanceInfo applyDelta(Delta delta) {
        if (!getId().equals(delta.getId())) {
            throw new UnsupportedOperationException("Cannot apply delta to instanceInfo with non-matching id: "
                    + delta.getId() + ", " + getId());
        }

        InstanceInfoBuilder newInstanceInfoBuilder = InstanceModel.getDefaultModel().newInstanceInfo().withInstanceInfo(this);
        return delta.applyTo(newInstanceInfoBuilder).build();
    }

    /**
     * Diff the current instanceInfo with another "newer" InstanceInfo, returning the results as a set of Deltas
     * iff the two instanceInfo have matching ids.
     * The version of the delta will be the version of the other ("newer") instanceInfo
     *
     * @param another the "newer" instanceInfo
     * @return the set of deltas, or null if the diff is invalid (InstanceInfos are null or id mismatch)
     */
    default Set<Delta<?>> diffNewer(InstanceInfo another) {
        return InstanceUtil.diff(this, another);
    }

    /**
     * Diff the current instanceInfo with another "older" InstanceInfo, returning the results as a set of Deltas
     * iff the two instanceInfo have matching ids.
     *
     * @param another the "older" instanceInfo
     * @return the set of deltas, or null if the diff is invalid (InstanceInfos are null or id mismatch)
     */
    default Set<Delta<?>> diffOlder(InstanceInfo another) {
        return InstanceUtil.diff(another, this);
    }
}
