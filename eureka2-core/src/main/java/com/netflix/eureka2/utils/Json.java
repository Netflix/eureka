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

package com.netflix.eureka2.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.UnpooledByteBufAllocator;

/**
 * A set of helper methods to convert to/from JSON format.
 *
 * @author Tomasz Bak
 */
public final class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper FORMATTED_MAPPER = new ObjectMapper();

    static {
        MAPPER.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        MAPPER.setVisibility(PropertyAccessor.GETTER, Visibility.NONE);
        MAPPER.setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE);
        MAPPER.setVisibility(PropertyAccessor.SETTER, Visibility.NONE);
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        MAPPER.setSerializationInclusion(Include.NON_NULL);

        FORMATTED_MAPPER.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        FORMATTED_MAPPER.setVisibility(PropertyAccessor.GETTER, Visibility.NONE);
        FORMATTED_MAPPER.setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE);
        FORMATTED_MAPPER.setVisibility(PropertyAccessor.SETTER, Visibility.NONE);
        FORMATTED_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        FORMATTED_MAPPER.setSerializationInclusion(Include.NON_NULL);
        FORMATTED_MAPPER.configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    private Json() {
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    public static ObjectMapper getFormattedMapper() {
        return FORMATTED_MAPPER;
    }

    public static <T> T fromJson(byte[] byteBuf, Class<T> type) {
        try {
            return MAPPER.readValue(byteBuf, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Provided buffer does not contain JSON object conforming to type " + type.getName(), e);
        }
    }

    public static <T> T fromJson(ByteBuf byteBuf, Class<T> type) {
        try {
            return MAPPER.readValue(new ByteBufInputStream(byteBuf), type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Provided buffer does not contain JSON object conforming to type " + type.getName(), e);
        }
    }

    public static <T> String toStringJson(T value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalArgumentException("Value of type " + value.getClass().getName() + " could not be serialized into JSON", e);
        }
    }

    public static <T> byte[] toByteArrayJson(T value) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MAPPER.writeValue(out, value);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Value of type " + value.getClass().getName() + " could not be serialized into JSON", e);
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
