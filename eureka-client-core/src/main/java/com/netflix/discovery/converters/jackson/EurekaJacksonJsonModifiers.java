package com.netflix.discovery.converters.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.KeyFormatter;
import com.netflix.discovery.converters.jackson.serializer.ApplicationsJsonBeanSerializer;
import com.netflix.discovery.converters.jackson.serializer.InstanceInfoJsonBeanSerializer;
import com.netflix.discovery.shared.Applications;

/**
 * @author Tomasz Bak
 */
final class EurekaJacksonJsonModifiers {

    private EurekaJacksonJsonModifiers() {
    }

    public static BeanSerializerModifier createJsonSerializerModifier(final KeyFormatter keyFormatter, final boolean compactMode) {
        return new BeanSerializerModifier() {
            @Override
            public JsonSerializer<?> modifySerializer(SerializationConfig config,
                                                      BeanDescription beanDesc, JsonSerializer<?> serializer) {
                if (beanDesc.getBeanClass().isAssignableFrom(Applications.class)) {
                    return new ApplicationsJsonBeanSerializer((BeanSerializerBase) serializer, keyFormatter);
                }
                if (beanDesc.getBeanClass().isAssignableFrom(InstanceInfo.class)) {
                    return new InstanceInfoJsonBeanSerializer((BeanSerializerBase) serializer, compactMode);
                }
                return serializer;
            }
        };
    }
}
