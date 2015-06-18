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
 * @author Tomasz Bak
 */
public class ReplicationHelloReply {

    private final Source source;
    private final boolean sendSnapshot;

    // For serialization frameworks
    protected ReplicationHelloReply() {
        this.source = null;
        this.sendSnapshot = false;
    }

    public ReplicationHelloReply(Source source, boolean sendSnapshot) {
        this.source = source;
        this.sendSnapshot = sendSnapshot;
    }

    public Source getSource() {
        return source;
    }

    public boolean isSendSnapshot() {
        return sendSnapshot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReplicationHelloReply)) return false;

        ReplicationHelloReply that = (ReplicationHelloReply) o;

        if (sendSnapshot != that.sendSnapshot) return false;
        if (source != null ? !source.equals(that.source) : that.source != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = source != null ? source.hashCode() : 0;
        result = 31 * result + (sendSnapshot ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ReplicationHelloReply{" +
                "source=" + source +
                ", sendSnapshot=" + sendSnapshot +
                '}';
    }
}
