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

package com.netflix.eureka2.model.channel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.StdSource;
import com.netflix.eureka2.spi.model.channel.ReplicationClientHello;

/**
 */
public class StdReplicationClientHello extends StdClientHello implements ReplicationClientHello {

    private final int registrySize;

    // For serializer
    private StdReplicationClientHello() {
        super(null);
        this.registrySize = 0;
    }

    public StdReplicationClientHello(Source clientSource, int registrySize) {
        super(clientSource);
        this.registrySize = registrySize;
    }

    @Override
    public int getRegistrySize() {
        return registrySize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        StdReplicationClientHello that = (StdReplicationClientHello) o;

        return registrySize == that.registrySize;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + registrySize;
        return result;
    }

    @Override
    public String toString() {
        return "StdReplicationClientHello{" +
                "registrySize=" + registrySize +
                '}';
    }

    @JsonCreator
    public static StdReplicationClientHello create(@JsonProperty("clientSource") StdSource clientSource,
                                                   @JsonProperty("registrySize") int registrySize) {
        return new StdReplicationClientHello(clientSource, registrySize);
    }
}
