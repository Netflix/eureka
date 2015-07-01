package com.netflix.discovery.converters;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.dataformat.xml.ser.XmlBeanSerializer;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.PortType;

/**
 * @author Tomasz Bak
 */
public final class InstanceInfoBeanSerializer {

    private InstanceInfoBeanSerializer() {
    }

    public static BeanSerializerModifier createJsonSerializer() {
        return new BeanSerializerModifier() {
            @Override
            public JsonSerializer<?> modifySerializer(SerializationConfig config,
                                                      BeanDescription beanDesc, JsonSerializer<?> serializer) {
                if (!beanDesc.getBeanClass().isAssignableFrom(InstanceInfo.class)) {
                    return serializer;
                }
                return new InstanceInfoJsonBeanSerializer((BeanSerializerBase) serializer);
            }
        };
    }

    public static BeanSerializerModifier createXmlSerializer() {
        return new BeanSerializerModifier() {
            @Override
            public JsonSerializer<?> modifySerializer(SerializationConfig config,
                                                      BeanDescription beanDesc, JsonSerializer<?> serializer) {
                if (!beanDesc.getBeanClass().isAssignableFrom(InstanceInfo.class)) {
                    return serializer;
                }
                return new InstanceInfoXmlBeanSerializer((BeanSerializerBase) serializer);
            }
        };
    }

    static class InstanceInfoJsonBeanSerializer extends BeanSerializer {
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

    static class InstanceInfoXmlBeanSerializer extends XmlBeanSerializer {
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

    static class InstanceInfoXmlBeanSerializerModifier extends BeanSerializerModifier {
        @Override
        public JsonSerializer<?> modifySerializer(SerializationConfig config,
                                                  BeanDescription beanDesc, JsonSerializer<?> serializer) {
            if (!beanDesc.getBeanClass().isAssignableFrom(InstanceInfo.class)) {
                return serializer;
            }
            return new InstanceInfoXmlBeanSerializer((BeanSerializerBase) serializer);
        }
    }
}
