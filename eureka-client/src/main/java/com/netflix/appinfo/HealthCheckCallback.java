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

package com.netflix.appinfo;

/**
 * Applications can implement this interface and register a callback with the
 * {@link com.netflix.discovery.EurekaClient#registerHealthCheckCallback(HealthCheckCallback)}.
 *
 * <p>
 * Your callback will be invoked every
 * {@link EurekaInstanceConfig#getLeaseRenewalIntervalInSeconds()} if the
 * instance is in {@link InstanceInfo.InstanceStatus#STARTING} status, we will
 * delay the callback until the status changes.Returning a false to the
 * checkHealth() method will mark the instance
 * {@link InstanceInfo.InstanceStatus#DOWN} with eureka.
 * </p>
 *
 * <p>
 * Eureka server normally just relies on <em>heartbeats</em> to identify the
 * <em>status</em> of an instance. Application could decide to implement their
 * own <em>healthpage</em> check here or use the built-in jersey resource
 * {@link HealthCheckResource}.
 * </p>
 *
 * @deprecated Use {@link com.netflix.appinfo.HealthCheckHandler} instead.
 * @author Karthik Ranganathan, Greg Kim
 */
@Deprecated
public interface HealthCheckCallback {
    /**
     * If false, the instance will be marked
     * {@link InstanceInfo.InstanceStatus#DOWN} with eureka. If the instance was
     * already marked {@link InstanceInfo.InstanceStatus#DOWN} , returning true
     * here will mark the instance back to
     * {@link InstanceInfo.InstanceStatus#UP}.
     *
     * @return true if the call back returns healthy, false otherwise.
     */
    boolean isHealthy();
}
