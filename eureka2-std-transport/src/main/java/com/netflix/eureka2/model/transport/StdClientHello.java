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
import com.netflix.eureka2.spi.model.ClientHello;

/**
 */
public class StdClientHello implements ClientHello {

    protected final Source clientSource;

    // for serializers
    private StdClientHello() {
        this(null);
    }

    public StdClientHello(Source clientSource) {
        this.clientSource = clientSource;
    }

    @Override
    public Source getClientSource() {
        return clientSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StdClientHello that = (StdClientHello) o;

        return clientSource != null ? clientSource.equals(that.clientSource) : that.clientSource == null;

    }

    @Override
    public int hashCode() {
        return clientSource != null ? clientSource.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "StdClientHello{clientSource=" + clientSource + '}';
    }

    @JsonCreator
    public static StdClientHello create(@JsonProperty("clientSource") StdSource clientSource) {
        return new StdClientHello(clientSource);
    }
}
