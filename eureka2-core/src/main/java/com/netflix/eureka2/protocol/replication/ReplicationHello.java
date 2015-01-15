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

/**
 * Hello message send by replication client (source), to identify itself and report the registry size.
 *
 * @author Tomasz Bak
 */
public class ReplicationHello {

    private final String sourceId;
    private final int registrySize;

    // For serialization frameworks
    protected ReplicationHello() {
        this.sourceId = null;
        this.registrySize = 0;
    }

    public ReplicationHello(String sourceId, int registrySize) {
        this.sourceId = sourceId;
        this.registrySize = registrySize;
    }

    public String getSourceId() {
        return sourceId;
    }

    public int getRegistrySize() {
        return registrySize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ReplicationHello that = (ReplicationHello) o;

        if (registrySize != that.registrySize)
            return false;
        if (sourceId != null ? !sourceId.equals(that.sourceId) : that.sourceId != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = sourceId != null ? sourceId.hashCode() : 0;
        result = 31 * result + registrySize;
        return result;
    }

    @Override
    public String toString() {
        return "ReplicationHello{" +
                "sourceId='" + sourceId + '\'' +
                ", registrySize=" + registrySize +
                '}';
    }
}
