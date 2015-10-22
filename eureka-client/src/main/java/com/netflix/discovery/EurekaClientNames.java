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

package com.netflix.discovery;

/**
 * @author Tomasz Bak
 */
public final class EurekaClientNames {

    /**
     * Eureka metric names consist of three parts [source].[component].[detailed name]:
     * <ul>
     *     <li>source - fixed to eurekaClient (and eurekaServer on the server side)</li>
     *     <li>component - Eureka component, like registry cache</li>
     *     <li>detailed name - a detailed metric name explaining its purpose</li>
     * </ul>
     */
    public static final String METRIC_PREFIX = "eurekaClient.";

    public static final String METRIC_REGISTRATION_PREFIX = METRIC_PREFIX + "registration.";

    public static final String METRIC_REGISTRY_PREFIX = METRIC_PREFIX + "registry.";

    public static final String METRIC_RESOLVER_PREFIX = METRIC_PREFIX + "resolver.";

    public static final String METRIC_TRANSPORT_PREFIX = METRIC_PREFIX + "transport.";

    public static final String RESOLVER = "resolver";
    public static final String BOOTSTRAP = "bootstrap";
    public static final String QUERY = "query";
    public static final String REGISTRATION = "registration";

    private EurekaClientNames() {
    }
}
