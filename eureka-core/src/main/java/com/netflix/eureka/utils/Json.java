/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka.utils;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.SerializationConfig.Feature;

/**
 * FIXME Do we need to abstract from a specific JSON parser implementation?
 *
 * @author Tomasz Bak
 */
public class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.setVisibility(JsonMethod.FIELD, Visibility.ANY);
        MAPPER.configure(Feature.FAIL_ON_EMPTY_BEANS, false);
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    public static <T> T fromJson(ByteBuf byteBuf, Class<T> type) {
        try {
            return MAPPER.readValue(new ByteBufInputStream(byteBuf), type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Provided buffer does not contain JSON object conforming to type " + type.getName(), e);
        }
    }

    public static <T> ByteBuf toByteBufJson(T value) {
        try {
            ByteBuf byteBuf = UnpooledByteBufAllocator.DEFAULT.buffer();
            MAPPER.writeValue(new ByteBufOutputStream(byteBuf), value);
            return byteBuf;
        } catch (IOException e) {
            throw new IllegalArgumentException("Value of type " + value.getClass().getName() + " could not be serialized into JSON", e);
        }
    }
}
