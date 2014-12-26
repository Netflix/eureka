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

package com.netflix.eureka2.registry.eviction;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.Source;

/**
 * @author Tomasz Bak
 */
public class EvictionItem {

    private final InstanceInfo instanceInfo;
    private final Source source;
    private final long expiryTime;

    public EvictionItem(InstanceInfo instanceInfo, Source source, long expiryTime) {
        this.instanceInfo = instanceInfo;
        this.source = source;
        this.expiryTime = expiryTime;
    }

    public InstanceInfo getInstanceInfo() {
        return instanceInfo;
    }

    public Source getSource() {
        return source;
    }

    public long getExpiryTime() {
        return expiryTime;
    }
}
