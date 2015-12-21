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

package com.netflix.eureka2.protocol.interest;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.StdDelta;
import com.netflix.eureka2.spi.protocol.interest.UpdateInstanceInfo;

/**
 */
public class StdUpdateInstanceInfo implements UpdateInstanceInfo {

    private final Set<StdDelta<?>> deltas;

    // For serialization framework
    protected StdUpdateInstanceInfo() {
        deltas = null;
    }

    public StdUpdateInstanceInfo(Set<StdDelta<?>> deltas) {
        this.deltas = deltas;
    }

    @Override
    public Set<Delta<?>> getDeltas() {
        return asDeltas(deltas);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StdUpdateInstanceInfo that = (StdUpdateInstanceInfo) o;

        return deltas != null ? deltas.equals(that.deltas) : that.deltas == null;

    }

    @Override
    public int hashCode() {
        return deltas != null ? deltas.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "StdUpdateInstanceInfo{deltas=" + deltas + '}';
    }

    @JsonCreator
    public static StdUpdateInstanceInfo create(@JsonProperty("deltas") Set<StdDelta<?>> deltas) {
        return new StdUpdateInstanceInfo(deltas);
    }

    private static Set<Delta<?>> asDeltas(Set deltas) {
        return deltas;
    }
}
