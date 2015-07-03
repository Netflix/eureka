package com.netflix.discovery.converters.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.BeanDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.impl.PropertyBasedCreator;
import com.fasterxml.jackson.databind.deser.impl.PropertyValueBuffer;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Applications;

/**
 * {@link Applications} and {@link InstanceInfo} objects require legacy formatting for some fields, that cannot
 * be accomplished by using standard Jackson mechanisms. This class overriddes {@link BeanDeserializer#_deserializeUsingPropertyBased}
 * method, and provides means to deal directly with deserialization of some fields, while preserving the default
 * behavior for the other.
 *
 * @author Tomasz Bak
 */
abstract class CustomizableBeanDeserializer extends BeanDeserializer {

    protected CustomizableBeanDeserializer(BeanDeserializerBase src) {
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
            if (isCustomField(propName)) {
                isComplete = handleCustomField(creator, buffer, propName, jp, ctxt);
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

    protected abstract boolean isCustomField(String propName);

    protected abstract boolean handleCustomField(PropertyBasedCreator creator, PropertyValueBuffer buffer,
                                                 String propName, JsonParser jp, DeserializationContext ctxt) throws IOException;
}
