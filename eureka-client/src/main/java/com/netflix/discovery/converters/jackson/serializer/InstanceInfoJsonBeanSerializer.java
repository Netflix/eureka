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
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.PortType;

/**
 * Custom bean serializer to deal with legacy port layout (check {@link InstanceInfo.PortWrapper} for more information).
 */
public class InstanceInfoJsonBeanSerializer extends BeanSerializer {

    private static final Map<String, String> EMPTY_MAP = Collections.singletonMap("@class", "java.util.Collections$EmptyMap");

    /**
     * As root mapper is wrapping values, we need a dedicated instance for map serialization.
     */
    private final ObjectMapper stringMapObjectMapper = new ObjectMapper();
    private final boolean compactMode;

    public InstanceInfoJsonBeanSerializer(BeanSerializerBase src, boolean compactMode) {
        super(src);
        this.compactMode = compactMode;
    }

    @Override
    protected void serializeFields(Object bean, JsonGenerator jgen0, SerializerProvider provider) throws IOException {
        super.serializeFields(bean, jgen0, provider);
        InstanceInfo instanceInfo = (InstanceInfo) bean;

        jgen0.writeFieldName("port");
        jgen0.writeStartObject();
        jgen0.writeNumberField("$", instanceInfo.getPort());
        jgen0.writeStringField("@enabled", Boolean.toString(instanceInfo.isPortEnabled(PortType.UNSECURE)));
        jgen0.writeEndObject();

        jgen0.writeFieldName("securePort");
        jgen0.writeStartObject();
        jgen0.writeNumberField("$", instanceInfo.getSecurePort());
        jgen0.writeStringField("@enabled", Boolean.toString(instanceInfo.isPortEnabled(PortType.SECURE)));
        jgen0.writeEndObject();

        // Save @class field for backward compatibility. Remove once all clients are migrated to the new codec
        if (!compactMode) {
            jgen0.writeFieldName("metadata");
            if (instanceInfo.getMetadata() == null || instanceInfo.getMetadata().isEmpty()) {
                stringMapObjectMapper.writeValue(jgen0, EMPTY_MAP);
            } else {
                stringMapObjectMapper.writeValue(jgen0, instanceInfo.getMetadata());
            }
        }
    }
}
