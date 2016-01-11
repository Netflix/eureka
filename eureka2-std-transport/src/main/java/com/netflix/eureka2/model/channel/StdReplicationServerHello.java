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
import com.netflix.eureka2.spi.model.channel.ReplicationServerHello;

/**
 */
public class StdReplicationServerHello extends StdServerHello implements ReplicationServerHello {

    // For serializer
    private StdReplicationServerHello() {
        super(null);
    }

    public StdReplicationServerHello(Source serverSource) {
        super(serverSource);
    }

    @Override
    public String toString() {
        return "StdReplicationServerHello{serverSource=" + getServerSource() + '}';
    }

    @JsonCreator
    public static StdReplicationServerHello create(@JsonProperty("serverSource") StdSource serverSource) {
        return new StdReplicationServerHello(serverSource);
    }
}
