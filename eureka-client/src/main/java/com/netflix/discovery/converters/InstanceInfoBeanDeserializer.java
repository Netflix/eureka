package com.netflix.discovery.converters;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.impl.PropertyBasedCreator;
import com.fasterxml.jackson.databind.deser.impl.PropertyValueBuffer;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.netflix.appinfo.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public abstract class InstanceInfoBeanDeserializer extends BeanDeserializer {

    protected InstanceInfoBeanDeserializer(BeanDeserializerBase src) {
        super(src);
    }

    @Override
    @SuppressWarnings("resource")
    protected Object _deserializeUsingPropertyBased(final JsonParser jp, final DeserializationContext ctxt) throws IOException {
        final PropertyBasedCreator creator = _propertyBasedCreator;
        PropertyValueBuffer buffer = creator.startBuilding(jp, ctxt, _objectIdReader);

        // 04-Jan-2010, tatu: May need to collect unknown properties for polymorphic cases
        TokenBuffer unknown = null;

        JsonToken t = jp.getCurrentToken();
        for (; t == JsonToken.FIELD_NAME; t = jp.nextToken()) {
            String propName = jp.getCurrentName();
            jp.nextToken(); // to point to value
            boolean isComplete = false;
            if ("port".equals(propName)) {
                isComplete = deserializePortSection(creator, buffer, jp, "port", "portEnabled");
                if (!isComplete) {
                    continue;
                }
            } else if ("securePort".equals(propName)) {
                isComplete = deserializePortSection(creator, buffer, jp, "securePort", "securePortEnabled");
                if (!isComplete) {
                    continue;
                }
            } else {
                // creator property?
                SettableBeanProperty creatorProp = creator.findCreatorProperty(propName);
                if (creatorProp != null) {
                    // Last creator property to set?
                    Object value = creatorProp.deserialize(jp, ctxt);
                    isComplete = buffer.assignParameter(creatorProp.getCreatorIndex(), value);
                    if (!isComplete) {
                        continue;
                    }
                }
            }
            if (isComplete) {
                jp.nextToken(); // to move to following FIELD_NAME/END_OBJECT
                Object bean;
                try {
                    bean = creator.build(ctxt, buffer);
                } catch (Exception e) {
                    wrapAndThrow(e, _beanType.getRawClass(), propName, ctxt);
                    bean = null; // never gets here
                }
                //  polymorphic?
                if (bean.getClass() != _beanType.getRawClass()) {
                    return handlePolymorphic(jp, ctxt, bean, unknown);
                }
                if (unknown != null) { // nope, just extra unknown stuff...
                    bean = handleUnknownProperties(ctxt, bean, unknown);
                }
                // or just clean?
                return deserialize(jp, ctxt, bean);
            }
            // Object Id property?
            if (buffer.readIdProperty(propName)) {
                continue;
            }
            // regular property? needs buffering
            SettableBeanProperty prop = _beanProperties.find(propName);
            if (prop != null) {
                buffer.bufferProperty(prop, prop.deserialize(jp, ctxt));
                continue;
            }
            // As per [JACKSON-313], things marked as ignorable should not be
            // passed to any setter
            if (_ignorableProps != null && _ignorableProps.contains(propName)) {
                handleIgnoredProperty(jp, ctxt, handledType(), propName);
                continue;
            }
            // "any property"?
            if (_anySetter != null) {
                buffer.bufferAnyProperty(_anySetter, propName, _anySetter.deserialize(jp, ctxt));
                continue;
            }
            // Ok then, let's collect the whole field; name and value
            if (unknown == null) {
                unknown = new TokenBuffer(jp);
            }
            unknown.writeFieldName(propName);
            unknown.copyCurrentStructure(jp);
        }

        // We hit END_OBJECT, so:
        Object bean;
        try {
            bean = creator.build(ctxt, buffer);
        } catch (Exception e) {
            wrapInstantiationProblem(e, ctxt);
            bean = null; // never gets here
        }
        if (unknown != null) {
            // polymorphic?
            if (bean.getClass() != _beanType.getRawClass()) {
                return handlePolymorphic(null, ctxt, bean, unknown);
            }
            // no, just some extra unknown properties
            return handleUnknownProperties(ctxt, bean, unknown);
        }
        return bean;
    }

    protected abstract boolean deserializePortSection(PropertyBasedCreator creator, PropertyValueBuffer buffer, JsonParser jp,
                                                      String portProperty, String portEnabledProperty) throws IOException;

    public static BeanDeserializerModifier createJsonDeserializerModifier() {
        return new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
                if (beanDesc.getType().getRawClass().isAssignableFrom(InstanceInfo.class)) {
                    return createJsonDeserializer((BeanDeserializerBase) deserializer);
                }
                return super.modifyDeserializer(config, beanDesc, deserializer);
            }
        };
    }

    public static BeanDeserializerModifier createXmlDeserializerModifier() {
        return new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
                if (beanDesc.getType().getRawClass().isAssignableFrom(InstanceInfo.class)) {
                    return createXmlDeserializer((BeanDeserializerBase) deserializer);
                }
                return super.modifyDeserializer(config, beanDesc, deserializer);
            }
        };
    }

    static InstanceInfoBeanDeserializer createJsonDeserializer(BeanDeserializerBase src) {
        return new InstanceInfoBeanDeserializer(src) {
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
                        if (jp.nextToken() == JsonToken.VALUE_NUMBER_INT) {
                            port = Integer.valueOf(jp.getText());
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
        };
    }

    static InstanceInfoBeanDeserializer createXmlDeserializer(BeanDeserializerBase src) {
        return new InstanceInfoBeanDeserializer(src) {
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
        };
    }

}
