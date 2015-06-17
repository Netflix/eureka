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

package com.netflix.eureka2.protocol.replication;

import com.netflix.eureka2.registry.Source;

/**
 * Hello message send by replication client (source), to identify itself and report the registry size.
 *
 * @author Tomasz Bak
 */
public class ReplicationHello {

    private final Source source;
    private final int registrySize;

    // For serialization frameworks
    protected ReplicationHello() {
        this.source = null;
        this.registrySize = 0;
    }

    public ReplicationHello(Source source, int registrySize) {
        this.source = source;
        this.registrySize = registrySize;
    }

    public Source getSource() {
        return source;
    }

    public int getRegistrySize() {
        return registrySize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReplicationHello)) return false;

        ReplicationHello that = (ReplicationHello) o;

        if (registrySize != that.registrySize) return false;
        if (source != null ? !source.equals(that.source) : that.source != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = source != null ? source.hashCode() : 0;
        result = 31 * result + registrySize;
        return result;
    }

    @Override
    public String toString() {
        return "ReplicationHello{" +
                "source=" + source +
                ", registrySize=" + registrySize +
                '}';
    }
}
