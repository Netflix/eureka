package com.netflix.discovery.converters.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.impl.PropertyBasedCreator;
import com.fasterxml.jackson.databind.deser.impl.PropertyValueBuffer;
import com.netflix.appinfo.InstanceInfo;

/**
 * Custom {@link InstanceInfo} deserializers that handles port/secure port according to the legacy rules.
 *
 * @author Tomasz Bak
 */
class InstanceInfoBeanDeserializers {

    abstract static class AbstractInstanceInfoDeserializer extends CustomizableBeanDeserializer {

        protected AbstractInstanceInfoDeserializer(BeanDeserializerBase src) {
            super(src);
        }

        @Override
        protected boolean isCustomField(String propName) {
            return "port".equals(propName) || "securePort".equals(propName);
        }

        @Override
        protected boolean handleCustomField(PropertyBasedCreator creator, PropertyValueBuffer buffer,
                                            String propName, JsonParser jp, DeserializationContext ctxt) throws IOException {
            boolean isComplete = false;
            if ("port".equals(propName)) {
                isComplete = deserializePortSection(creator, buffer, jp, "port", "portEnabled");
            } else if ("securePort".equals(propName)) {
                isComplete = deserializePortSection(creator, buffer, jp, "securePort", "securePortEnabled");
            }
            return isComplete;
        }

        protected abstract boolean deserializePortSection(PropertyBasedCreator creator, PropertyValueBuffer buffer, JsonParser jp,
                                                          String portProperty, String portEnabledProperty) throws IOException;
    }

    static class InstanceInfoJsonBeanDeserializer extends AbstractInstanceInfoDeserializer {

        InstanceInfoJsonBeanDeserializer(BeanDeserializerBase src) {
            super(src);
        }

        @Override
        protected boolean deserializePortSection(PropertyBasedCreator creator, PropertyValueBuffer buffer, JsonParser jp,
                                                 String portProperty, String portEnabledProperty) throws IOException {
            Boolean enabled = null;
            Integer port = null;
            while (jp.nextToken() == JsonToken.FIELD_NAME) {
                if ("@enabled".equals(jp.getCurrentName())) {
                    if (jp.nextToken() == JsonToken.VALUE_STRING) {
                        enabled = Boolean.valueOf(jp.getText());
                    } else {
                        throw new JsonParseException("Invalid port JSON subdocument structure", jp.getCurrentLocation());
                    }
                } else if ("$".equals(jp.getCurrentName())) {
                    if (jp.nextToken() == JsonToken.VALUE_STRING) {
                        port = Integer.valueOf(jp.getText());
                    } else if (jp.getCurrentToken() == JsonToken.VALUE_NUMBER_INT) {
                        port = jp.getIntValue();
                    } else {
                        throw new JsonParseException("Invalid port JSON subdocument structure", jp.getCurrentLocation());
                    }
                } else {
                    throw new JsonParseException("Invalid port JSON subdocument structure", jp.getCurrentLocation());
                }
            }
            boolean last = false;
            if (port != null) {
                SettableBeanProperty portProp = creator.findCreatorProperty(portProperty);
                last |= buffer.assignParameter(portProp.getCreatorIndex(), port);
            }
            if (enabled != null) {
                SettableBeanProperty enabledProp = creator.findCreatorProperty(portEnabledProperty);
                last |= buffer.assignParameter(enabledProp.getCreatorIndex(), enabled);
            }
            return last;
        }
    }

    static class InstanceInfoXmlBeanDeserializer extends AbstractInstanceInfoDeserializer {

        InstanceInfoXmlBeanDeserializer(BeanDeserializerBase src) {
            super(src);
        }

        @Override
        protected boolean deserializePortSection(PropertyBasedCreator creator, PropertyValueBuffer buffer, JsonParser jp,
                                                 String portProperty, String portEnabledProperty) throws IOException {
            if (jp.nextToken() == JsonToken.FIELD_NAME && "enabled".equals(jp.getCurrentName())) {
                if (jp.nextToken() == JsonToken.VALUE_STRING) {
                    boolean enabled = Boolean.valueOf(jp.getText());
                    if (jp.nextToken() == JsonToken.FIELD_NAME && jp.nextToken() == JsonToken.VALUE_STRING) {
                        int port = Integer.parseInt(jp.getText());

                        SettableBeanProperty portProp = creator.findCreatorProperty(portProperty);
                        buffer.assignParameter(portProp.getCreatorIndex(), port);
                        SettableBeanProperty enabledProp = creator.findCreatorProperty(portEnabledProperty);
                        jp.nextToken();
                        return buffer.assignParameter(enabledProp.getCreatorIndex(), enabled);
                    }
                }
            }
            throw new JsonParseException("Expected port/securePort section of InstanceInfo object", jp.getCurrentLocation());
        }
    }
}
