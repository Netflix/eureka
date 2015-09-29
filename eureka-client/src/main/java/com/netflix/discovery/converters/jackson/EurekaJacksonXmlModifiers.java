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

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.KeyFormatter;
import com.netflix.discovery.converters.jackson.InstanceInfoBeanDeserializers.InstanceInfoXmlBeanDeserializer;
import com.netflix.discovery.shared.Applications;

/**
 * @author Tomasz Bak
 */
public final class EurekaJacksonXmlModifiers {

    private EurekaJacksonXmlModifiers() {
    }

    public static BeanDeserializerModifier createXmlDeserializerModifier(final KeyFormatter keyFormatter, final boolean compactMode) {
        return new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
                if (beanDesc.getType().getRawClass().isAssignableFrom(Applications.class)) {
                    return new ApplicationsBeanDeserializer((BeanDeserializerBase) deserializer, keyFormatter);
                }
                if (beanDesc.getType().getRawClass().isAssignableFrom(InstanceInfo.class)) {
                    return new InstanceInfoXmlBeanDeserializer((BeanDeserializerBase) deserializer, compactMode);
                }
                return super.modifyDeserializer(config, beanDesc, deserializer);
            }
        };
    }

    public static BeanSerializerModifier createXmlSerializerModifier(final KeyFormatter keyFormatter) {
        return new BeanSerializerModifier() {
            @Override
            public JsonSerializer<?> modifySerializer(SerializationConfig config,
                                                      BeanDescription beanDesc, JsonSerializer<?> serializer) {
                if (beanDesc.getBeanClass().isAssignableFrom(Applications.class)) {
                    return new ApplicationsXmlBeanSerializer((BeanSerializerBase) serializer, keyFormatter);
                }
                if (beanDesc.getBeanClass().isAssignableFrom(InstanceInfo.class)) {
                    return new InstanceInfoXmlBeanSerializer((BeanSerializerBase) serializer);
                }
                return serializer;
            }
        };
    }
}
