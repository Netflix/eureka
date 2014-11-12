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

package com.netflix.rx.eureka.server.registry;

import com.netflix.rx.eureka.registry.InstanceInfo;
import rx.Observable;

/**
 * {@link InstanceInfo} objects are added to the eviction queue, when their corresponding
 * transport channel is abruptly disconnected, either due to missed heartbeat or network failure.
 * Instances for which unregister request is received, are never put into the eviction queue, but
 * instead are removed immediately from the registry.
 *
 * @author Tomasz Bak
 */
public interface EvictionQueue {

    /**
     * @param instanceInfo item to be added to the eviction queue
     * @param source source from which the given instance info comes from
     */
    void add(InstanceInfo instanceInfo, Source source);

    /**
     * Observable of {@link EvictionItem} objects to be removed from the registry. The observable implements
     * backpressure protocol, which is important during registry self preservation mode.
     *
     * @return {@link EvictionItem} objects to be removed from the registry
     */
    Observable<EvictionItem> pendingEvictions();
}

