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

package com.netflix.eureka2.model.transport;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.netflix.eureka2.spi.model.transport.ProtocolMessageEnvelope;

public class StdProtocolMessageEnvelope implements ProtocolMessageEnvelope {

    private final ProtocolType protocolType;

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = As.PROPERTY, property = "class")
    private final Object message;

    // For serialization framework.
    protected StdProtocolMessageEnvelope() {
        this.protocolType = null;
        this.message = null;
    }

    public StdProtocolMessageEnvelope(ProtocolType protocolType, Object message) {
        this.protocolType = protocolType;
        this.message = message;
    }

    @Override
    public ProtocolType getProtocolType() {
        return protocolType;
    }

    @Override
    public Object getMessage() {
        return message;
    }
}
