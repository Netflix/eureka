package com.netflix.discovery.converters.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.dataformat.xml.ser.XmlBeanSerializer;
import com.netflix.discovery.converters.KeyFormatter;
import com.netflix.discovery.shared.Applications;

/**
 * {@link Applications} instances have legacy field names with configurable formatting.
 * These are handled explicitly by this serializer.
 *
 * @author Tomasz Bak
 */
class ApplicationsBeanSerializers {

    static class ApplicationsJsonBeanSerializer extends BeanSerializer {
        private final String versionKey;
        private final String appsHashCodeKey;

        ApplicationsJsonBeanSerializer(BeanSerializerBase src, KeyFormatter keyFormatter) {
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

    static class ApplicationsXmlBeanSerializer extends XmlBeanSerializer {
        private final String versionKey;
        private final String appsHashCodeKey;

        ApplicationsXmlBeanSerializer(BeanSerializerBase src, KeyFormatter keyFormatter) {
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
}
