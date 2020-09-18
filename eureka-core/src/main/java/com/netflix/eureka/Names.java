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

package com.netflix.eureka;

/**
 * @author Tomasz Bak
 */
public class Names {

    /**
     * Eureka metric names consist of three parts [source].[component].[detailed name]:
     * <ul>
     *     <li>source - fixed to eurekaServer (and eurekaClient on the client side)</li>
     *     <li>component - Eureka component, like REST layer, replication, etc</li>
     *     <li>detailed name - a detailed metric name explaining its purpose</li>
     * </ul>
     */
    public static final String METRIC_PREFIX = "eurekaServer.";

    public static final String METRIC_REPLICATION_PREFIX = METRIC_PREFIX + "replication.";

    public static final String METRIC_REGISTRY_PREFIX = METRIC_PREFIX + "registry.";

    public static final String REMOTE = "remote";
}
