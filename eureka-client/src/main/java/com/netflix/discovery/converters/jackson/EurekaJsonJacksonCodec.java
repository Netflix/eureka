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

package com.netflix.discovery.converters.jackson;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.discovery.converters.KeyFormatter;

/**
 * @author Tomasz Bak
 */
public class EurekaJsonJacksonCodec extends AbstractEurekaJacksonCodec {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    public EurekaJsonJacksonCodec() {
        this(KeyFormatter.defaultKeyFormatter(), false);
    }

    public EurekaJsonJacksonCodec(final KeyFormatter keyFormatter, boolean compact) {
        // JSON
        SimpleModule jsonModule = new SimpleModule();
        jsonModule.setSerializerModifier(EurekaJacksonModifiers.createJsonSerializerModifier(keyFormatter));
        jsonModule.setDeserializerModifier(EurekaJacksonModifiers.createJsonDeserializerModifier(keyFormatter, compact));
        jsonMapper.registerModule(jsonModule);
        jsonMapper.setSerializationInclusion(Include.NON_NULL);
        jsonMapper.configure(SerializationFeature.WRAP_ROOT_VALUE, true);
        jsonMapper.configure(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED, true);
        jsonMapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
        jsonMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        if (compact) {
            addMiniConfig(jsonMapper);
        }
    }

    @Override
    public ObjectMapper getObjectMapper() {
        return jsonMapper;
    }
}
