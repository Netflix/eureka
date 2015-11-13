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

package com.netflix.eureka2.codec.jackson;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.netflix.eureka2.model.datacenter.DataCenterInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import com.netflix.eureka2.model.instance.InstanceInfoField;
import com.netflix.eureka2.model.instance.InstanceInfoField.Name;
import com.netflix.eureka2.model.instance.StdDelta;
import com.netflix.eureka2.model.instance.StdServicePort;

/**
 */
public class DeltaDeserializer extends JsonDeserializer<StdDelta<?>> {
    @Override
    public StdDelta<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        String id = null;
        InstanceInfoField<Object> field = null;
        Object value = null;

        onToken(p, JsonToken.START_OBJECT);
        while (p.nextToken() != JsonToken.END_OBJECT) {
            onToken(p, JsonToken.FIELD_NAME);
            String fieldName = p.getCurrentName();
            p.nextToken();
            switch (fieldName) {
                case "id":
                    onToken(p, JsonToken.VALUE_STRING);
                    id = p.getText();
                    break;
                case "field":
                    onToken(p, JsonToken.VALUE_STRING);
                    field = forName(p, p.getText());
                    break;
                case "value":
                    if (field == null) {
                        throw new JsonParseException("fieldName not present, or after the value", p.getCurrentLocation());
                    }
                    value = readValue(p, field);
                    break;
                default:
                    throw new JsonParseException("Unrecognized field " + fieldName, p.getCurrentLocation());
            }
        }
        return (StdDelta<?>) new StdDelta.Builder().withId(id).withDelta(field, value).build();
    }

    private static Object readValue(JsonParser p, InstanceInfoField<Object> field) throws IOException {
        Object value = null;
        if(p.getCurrentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        switch (field.getFieldName()) {
            case AppGroup:
            case App:
            case Asg:
            case VipAddress:
            case SecureVipAddress:
            case HomePageUrl:
            case StatusPageUrl:
                onToken(p, JsonToken.VALUE_STRING);
                value = p.getText();
                break;
            case Status:
                onToken(p, JsonToken.VALUE_STRING);
                String statusText = p.getText();
                try {
                    value = Status.valueOf(statusText);
                } catch (IllegalArgumentException e) {
                    throw new JsonParseException("Unrecognized Status value " + statusText, p.getCurrentLocation());
                }
                break;
            case Ports:
                onToken(p, JsonToken.START_ARRAY);
                TypeReference<Set<StdServicePort>> typeRef = new TypeReference<Set<StdServicePort>>() {
                };
                value = JacksonEurekaCodec.MAPPER.readValue(p, typeRef);
                break;
            case HealthCheckUrls:
                onToken(p, JsonToken.START_ARRAY);
                TypeReference<Set<String>> htypeRef = new TypeReference<Set<String>>() {
                };
                value = JacksonEurekaCodec.MAPPER.readValue(p, htypeRef);
                break;
            case MetaData:
                onToken(p, JsonToken.START_OBJECT);
                TypeReference<Map<String, String>> mtypeRef = new TypeReference<Map<String, String>>() {
                };
                value = JacksonEurekaCodec.MAPPER.readValue(p, mtypeRef);
                break;
            case DataCenterInfo:
                onToken(p, JsonToken.START_OBJECT);
                value = JacksonEurekaCodec.MAPPER.readValue(p, DataCenterInfo.class);
                break;
        }
        return value;
    }

    private static void onToken(JsonParser p, JsonToken expectedToken) throws IOException {
        JsonToken actual = p.getCurrentToken();
        if (actual != expectedToken) {
            throw new JsonParseException("Unexpected token " + actual, p.getCurrentLocation());
        }
    }

    private static InstanceInfoField<Object> forName(JsonParser p, String fieldName) throws JsonParseException {
        Name name;
        try {
            name = Name.forName(fieldName);
        } catch (Exception ignore) {
            throw new JsonParseException("Unexpected fieldName value " + fieldName, p.getCurrentLocation());
        }
        return InstanceInfoField.forName(name);
    }
}
