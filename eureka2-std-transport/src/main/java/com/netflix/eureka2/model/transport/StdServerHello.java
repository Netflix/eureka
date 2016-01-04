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

package com.netflix.eureka2.model.transport;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.StdSource;
import com.netflix.eureka2.spi.model.ServerHello;

/**
 */
public class StdServerHello implements ServerHello {

    private final Source serverSource;

    // For serializer
    private StdServerHello() {
        this.serverSource = null;
    }

    public StdServerHello(Source serverSource) {
        this.serverSource = serverSource;
    }

    @Override
    public Source getServerSource() {
        return serverSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StdServerHello that = (StdServerHello) o;

        return serverSource != null ? serverSource.equals(that.serverSource) : that.serverSource == null;

    }

    @Override
    public int hashCode() {
        return serverSource != null ? serverSource.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "StdServerHello{serverSource=" + serverSource + '}';
    }

    @JsonCreator
    public static StdServerHello create(@JsonProperty("serverSource") StdSource clientSource) {
        return new StdServerHello(clientSource);
    }
}
