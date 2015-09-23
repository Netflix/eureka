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
import com.netflix.discovery.converters.jackson.InstanceInfoBeanDeserializers.InstanceInfoJsonBeanDeserializer;
import com.netflix.discovery.shared.Applications;

/**
 * @author Tomasz Bak
 */
final class EurekaJacksonJsonModifiers {

    private EurekaJacksonJsonModifiers() {
    }

    public static BeanDeserializerModifier createJsonDeserializerModifier(final KeyFormatter keyFormatter, final boolean compactMode) {
        return new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
                if (beanDesc.getType().getRawClass().isAssignableFrom(Applications.class)) {
                    return new ApplicationsBeanDeserializer((BeanDeserializerBase) deserializer, keyFormatter);
                }
                if (beanDesc.getType().getRawClass().isAssignableFrom(InstanceInfo.class)) {
                    return new InstanceInfoJsonBeanDeserializer((BeanDeserializerBase) deserializer, compactMode);
                }
                return super.modifyDeserializer(config, beanDesc, deserializer);
            }
        };
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
