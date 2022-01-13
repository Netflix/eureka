/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.discovery.converters.jackson.serializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;

/**
 * Deserialize {@link Application} from XML directly due to issues with Jackson 2.6.x
 * (this is not needed for Jackson 2.5.x).
 */
public class ApplicationXmlDeserializer extends StdDeserializer<Application> {

    public ApplicationXmlDeserializer() {
        super(Application.class);
    }

    @Override
    public Application deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        String name = null;
        List<InstanceInfo> instances = new ArrayList<>();
        while (jp.nextToken() == JsonToken.FIELD_NAME) {
            String fieldName = jp.getCurrentName();
            jp.nextToken(); // to point to value
            if ("name".equals(fieldName)) {
                name = jp.getValueAsString();
            } else if ("instance".equals(fieldName)) {
                instances.add(jp.readValueAs(InstanceInfo.class));
            } else {
                throw new JsonMappingException("Unexpected field " + fieldName, jp.getCurrentLocation());
            }
        }
        return new Application(name, instances);
    }
}
