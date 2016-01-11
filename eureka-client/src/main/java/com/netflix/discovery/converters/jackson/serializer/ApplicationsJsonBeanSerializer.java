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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.netflix.discovery.converters.KeyFormatter;
import com.netflix.discovery.shared.Applications;

/**
 * Support custom formatting of {@link Applications#appsHashCode} and {@link Applications#versionDelta}.
 */
public class ApplicationsJsonBeanSerializer extends BeanSerializer {
    private final String versionKey;
    private final String appsHashCodeKey;

    public ApplicationsJsonBeanSerializer(BeanSerializerBase src, KeyFormatter keyFormatter) {
        super(src);
        versionKey = keyFormatter.formatKey("versions_delta");
        appsHashCodeKey = keyFormatter.formatKey("apps_hashcode");
    }

    @Override
    protected void serializeFields(Object bean, JsonGenerator jgen0, SerializerProvider provider) throws IOException {
        super.serializeFields(bean, jgen0, provider);
        Applications applications = (Applications) bean;

        if (applications.getVersion() != null) {
            jgen0.writeStringField(versionKey, Long.toString(applications.getVersion()));
        }
        if (applications.getAppsHashCode() != null) {
            jgen0.writeStringField(appsHashCodeKey, applications.getAppsHashCode());
        }
    }
}
