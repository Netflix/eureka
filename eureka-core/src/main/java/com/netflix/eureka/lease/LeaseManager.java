/*
 * Copyright 2012 Netflix, Inc.
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

package com.netflix.eureka.lease;

import com.netflix.eureka.registry.AbstractInstanceRegistry;

/**
 * This class is responsible for creating/renewing and evicting a <em>lease</em>
 * for a particular instance.
 *
 * <p>
 * Leases determine what instances receive traffic. When there is no renewal
 * request from the client, the lease gets expired and the instances are evicted
 * out of {@link AbstractInstanceRegistry}. This is key to instances receiving traffic
 * or not.
 * <p>
 *
 * @author Karthik Ranganathan, Greg Kim
 *
 * @param <T>
 */
public interface LeaseManager<T> {

    /**
     * Assign a new {@link Lease} to the passed in {@link T}.
     *
     * @param r
     *            - T to register
     * @param leaseDuration
     * @param isReplication
     *            - whether this is a replicated entry from another eureka node.
     */
    void register(T r, int leaseDuration, boolean isReplication);

    /**
     * Cancel the {@link Lease} associated w/ the passed in <code>appName</code>
     * and <code>id</code>.
     *
     * @param appName
     *            - unique id of the application.
     * @param id
     *            - unique id within appName.
     * @param isReplication
     *            - whether this is a replicated entry from another eureka node.
     * @return true, if the operation was successful, false otherwise.
     */
    boolean cancel(String appName, String id, boolean isReplication);

    /**
     * Renew the {@link Lease} associated w/ the passed in <code>appName</code>
     * and <code>id</code>.
     *
     * @param id
     *            - unique id within appName
     * @param isReplication
     *            - whether this is a replicated entry from another ds node
     * @return whether the operation of successful
     */
    boolean renew(String appName, String id, boolean isReplication);

    /**
     * Evict {@link T}s with expired {@link Lease}(s).
     */
    void evict();
}
