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

package com.netflix.discovery.converters.jackson.serializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.netflix.appinfo.InstanceInfo;

/**
 * Due to issues with properly mapping  port XML sub-document, handle deserialization directly.
 */
public class PortWrapperXmlDeserializer extends StdDeserializer<InstanceInfo.PortWrapper> {

    public PortWrapperXmlDeserializer() {
        super(InstanceInfo.PortWrapper.class);
    }

    @Override
    public InstanceInfo.PortWrapper deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        boolean enabled = false;
        int port = 0;
        while (jp.nextToken() == JsonToken.FIELD_NAME) {
            String fieldName = jp.getCurrentName();
            jp.nextToken(); // to point to value
            if ("enabled".equals(fieldName)) {
                enabled = Boolean.valueOf(jp.getValueAsString());
            } else if (fieldName == null || "".equals(fieldName)) {
                String value = jp.getValueAsString();
                port = value == null ? 0 : Integer.parseInt(value);
            } else {
                throw new JsonMappingException("Unexpected field " + fieldName, jp.getCurrentLocation());
            }
        }
        return new InstanceInfo.PortWrapper(enabled, port);
    }
}
