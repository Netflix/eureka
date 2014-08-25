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

package com.netflix.eureka.protocol.discovery;

import com.netflix.eureka.registry.Delta;

/**
 * @author Tomasz Bak
 */
public class UpdateInstanceInfo implements InterestSetNotification {

    private final Delta<?> delta;

    // For serialization framework
    protected UpdateInstanceInfo() {
        delta = null;
    }

    public UpdateInstanceInfo(Delta<?> delta) {
        this.delta = delta;
    }

    public Delta<?> getDelta() {
        return delta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UpdateInstanceInfo that = (UpdateInstanceInfo) o;

        if (delta != null ? !delta.equals(that.delta) : that.delta != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return delta != null ? delta.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "UpdateInstanceInfo{delta=" + delta + '}';
    }
}
