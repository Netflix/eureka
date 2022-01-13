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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.KeyFormatter;
import com.netflix.discovery.converters.jackson.mixin.ApplicationsJsonMixIn;
import com.netflix.discovery.converters.jackson.mixin.InstanceInfoJsonMixIn;
import com.netflix.discovery.shared.Applications;

/**
 * JSON codec defaults to unwrapped mode, as ReplicationList in the replication channel, must be serialized
 * unwrapped. The wrapping mode is configured separately for each type, based on presence of
 * {@link com.fasterxml.jackson.annotation.JsonRootName} annotation.
 *
 * @author Tomasz Bak
 */
public class EurekaJsonJacksonCodec extends AbstractEurekaJacksonCodec {

    private final ObjectMapper wrappedJsonMapper;
    private final ObjectMapper unwrappedJsonMapper;

    private final Map<Class<?>, ObjectMapper> mappers = new ConcurrentHashMap<>();

    public EurekaJsonJacksonCodec() {
        this(KeyFormatter.defaultKeyFormatter(), false);
    }

    public EurekaJsonJacksonCodec(final KeyFormatter keyFormatter, boolean compact) {
        this.unwrappedJsonMapper = createObjectMapper(keyFormatter, compact, false);
        this.wrappedJsonMapper = createObjectMapper(keyFormatter, compact, true);
    }

    private ObjectMapper createObjectMapper(KeyFormatter keyFormatter, boolean compact, boolean wrapped) {
        ObjectMapper newMapper = new ObjectMapper();
        SimpleModule jsonModule = new SimpleModule();
        jsonModule.setSerializerModifier(EurekaJacksonJsonModifiers.createJsonSerializerModifier(keyFormatter, compact));

        newMapper.registerModule(jsonModule);
        newMapper.setSerializationInclusion(Include.NON_NULL);
        newMapper.configure(SerializationFeature.WRAP_ROOT_VALUE, wrapped);
        newMapper.configure(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED, false);
        newMapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, wrapped);
        newMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        newMapper.addMixIn(Applications.class, ApplicationsJsonMixIn.class);
        if (compact) {
            addMiniConfig(newMapper);
        } else {
            newMapper.addMixIn(InstanceInfo.class, InstanceInfoJsonMixIn.class);
        }
        return newMapper;
    }

    @Override
    public <T> ObjectMapper getObjectMapper(Class<T> type) {
        ObjectMapper mapper = mappers.get(type);
        if (mapper == null) {
            mapper = hasJsonRootName(type) ? wrappedJsonMapper : unwrappedJsonMapper;
            mappers.put(type, mapper);
        }
        return mapper;
    }
}
