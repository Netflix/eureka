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

package com.netflix.discovery.converters.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.PortType;

/**
 * @author Tomasz Bak
 */
class InstanceInfoJsonBeanSerializer extends BeanSerializer {
    InstanceInfoJsonBeanSerializer(BeanSerializerBase src) {
        super(src);
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
    }
}
