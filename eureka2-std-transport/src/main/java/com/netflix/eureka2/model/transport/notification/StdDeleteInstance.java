/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.eureka2.model.transport.notification;

import com.netflix.eureka2.spi.model.transport.notification.DeleteInstance;

/**
 */
public class StdDeleteInstance implements DeleteInstance {

    private final String instanceId;

    // For serialization frameworks
    protected StdDeleteInstance() {
        instanceId = null;
    }

    public StdDeleteInstance(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        StdDeleteInstance that = (StdDeleteInstance) o;

        if (instanceId != null ? !instanceId.equals(that.instanceId) : that.instanceId != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return instanceId != null ? instanceId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "StdDeleteInstance{instanceId='" + instanceId + '\'' + '}';
    }
}
