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

package com.netflix.eureka2.protocol;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

public class ProtocolMessageEnvelope {

    public enum ProtocolType { Registration, Interest, Replication }

    private final ProtocolType protocolType;

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = As.PROPERTY, property = "class")
    private final Object message;

    // For serialization framework.
    protected ProtocolMessageEnvelope() {
        this.protocolType = null;
        this.message = null;
    }

    public ProtocolMessageEnvelope(ProtocolType protocolType, Object message) {
        this.protocolType = protocolType;
        this.message = message;
    }

    public ProtocolType getProtocolType() {
        return protocolType;
    }

    public Object getMessage() {
        return message;
    }

    public static <T> ProtocolMessageEnvelope registrationEnvelope(T message) {
        return new ProtocolMessageEnvelope(ProtocolType.Registration, message);
    }

    public static <T> ProtocolMessageEnvelope interestEnvelope(T message) {
        return new ProtocolMessageEnvelope(ProtocolType.Interest, message);
    }

    public static <T> ProtocolMessageEnvelope replicationEnvelope(T message) {
        return new ProtocolMessageEnvelope(ProtocolType.Replication, message);
    }
}
