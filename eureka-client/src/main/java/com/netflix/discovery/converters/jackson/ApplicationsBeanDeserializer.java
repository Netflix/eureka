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
import com.netflix.discovery.converters.KeyFormatter;
import com.netflix.discovery.shared.Applications;

/**
 * {@link Applications} instances have legacy field names with configurable formatting.
 * These are handled explicitly by this deserializer.

 * @author Tomasz Bak
 */
class ApplicationsBeanDeserializer extends CustomizableBeanDeserializer {

    private final String versionKey;
    private final String appsHashCodeKey;

    protected ApplicationsBeanDeserializer(BeanDeserializerBase src, KeyFormatter keyFormatter) {
        super(src);
        versionKey = keyFormatter.formatKey("versions_delta");
        appsHashCodeKey = keyFormatter.formatKey("apps_hashcode");
    }

    @Override
    protected boolean isCustomField(String propName) {
        return versionKey.equals(propName) || appsHashCodeKey.equals(propName);
    }

    @Override
    protected boolean handleCustomField(PropertyBasedCreator creator, PropertyValueBuffer buffer,
                                        String propName, JsonParser jp, DeserializationContext ctxt) throws IOException {
        if (versionKey.equals(propName)) {
            long versionDelta;
            if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
                versionDelta = Long.valueOf(jp.getText());
            } else if (jp.getCurrentToken() == JsonToken.VALUE_NUMBER_INT) {
                versionDelta = jp.getLongValue();
            } else {
                throw new JsonParseException("Invalid versionDelta JSON subdocument structure", jp.getCurrentLocation());
            }

            SettableBeanProperty versionProp = creator.findCreatorProperty("versionDelta");
            return buffer.assignParameter(versionProp.getCreatorIndex(), versionDelta);
        } else if (appsHashCodeKey.equals(propName)) {
            String appsHashCode;
            if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
                appsHashCode = jp.getText();
            } else {
                throw new JsonParseException("Invalid appsHashCode JSON subdocument structure", jp.getCurrentLocation());
            }

            SettableBeanProperty appsHashCodeProp = creator.findCreatorProperty("appsHashCode");
            return buffer.assignParameter(appsHashCodeProp.getCreatorIndex(), appsHashCode);
        } else {
            throw new JsonParseException("Expected versionDelta/appsHashCode hashcode fields of Applications object", jp.getCurrentLocation());
        }
    }
}
