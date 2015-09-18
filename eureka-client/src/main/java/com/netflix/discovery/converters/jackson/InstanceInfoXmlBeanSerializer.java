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
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.dataformat.xml.ser.XmlBeanSerializer;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.PortType;

/**
 * @author Tomasz Bak
 */
class InstanceInfoXmlBeanSerializer extends XmlBeanSerializer {
    InstanceInfoXmlBeanSerializer(BeanSerializerBase src) {
        super(src);
    }

    @Override
    protected void serializeFields(Object bean, JsonGenerator jgen0, SerializerProvider provider) throws IOException {
        super.serializeFields(bean, jgen0, provider);
        InstanceInfo instanceInfo = (InstanceInfo) bean;

        ToXmlGenerator xgen = (ToXmlGenerator) jgen0;

        xgen.writeFieldName("port");
        xgen.writeStartObject();
        xgen.setNextIsAttribute(true);
        xgen.writeStringField("enabled", Boolean.toString(instanceInfo.isPortEnabled(PortType.UNSECURE)));
        xgen.setNextIsAttribute(false);
        xgen.setNextIsUnwrapped(true);
        xgen.writeString(Integer.toString(instanceInfo.getPort()));
        xgen.writeEndObject();

        xgen.writeFieldName("securePort");
        xgen.writeStartObject();
        xgen.setNextIsAttribute(true);
        xgen.writeStringField("enabled", Boolean.toString(instanceInfo.isPortEnabled(PortType.SECURE)));
        xgen.setNextIsAttribute(false);
        xgen.setNextIsUnwrapped(true);
        xgen.writeString(Integer.toString(instanceInfo.getSecurePort()));
        xgen.writeEndObject();
    }
}
