/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka2.server.service.selfinfo;

import com.netflix.eureka2.model.instance.InstanceInfo;
import rx.Observable;

/**
 * {@link SelfInfoResolver} provides observable of the local node's {@link InstanceInfo} objects.
 * Apart of the standard {@link InstanceInfo} data, Eureka sets meta-data that indicate what is
 * the server type (write or read), or identity of the write cluster (for read nodes).
 *
 * @author David Liu
 */
public interface SelfInfoResolver {

    /**
     * Meta annotation key which value denotes Eureka's write cluster id.
     */
    String META_EUREKA_WRITE_CLUSTER_ID = "eureka2.writeClusterId";

    /**
     * Meta annotation key for Eureka server type (write/read).
     */
    String META_EUREKA_SERVER_TYPE = "eureka2.serverType";

    Observable<InstanceInfo> resolve();
}
