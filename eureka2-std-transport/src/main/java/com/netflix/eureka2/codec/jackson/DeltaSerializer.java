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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.netflix.eureka2.model.instance.StdDelta;

public class DeltaSerializer extends JsonSerializer<StdDelta<?>> {
    @Override
    public void serialize(StdDelta<?> delta, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
        gen.writeStartObject();
        gen.writeStringField("id", delta.getId());
        gen.writeStringField("field", delta.getField().getFieldName().name());
        Object value = delta.getValue();

        if(value == null) {
            gen.writeStringField("value", null);
        }else if (value.getClass().isPrimitive() || value.getClass() == String.class) {
            gen.writeStringField("value", value.toString());
        } else {
            gen.writeObjectField("value", value);
        }
        gen.writeEndObject();
    }
}
