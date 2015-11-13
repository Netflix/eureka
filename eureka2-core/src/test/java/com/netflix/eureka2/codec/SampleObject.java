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

package com.netflix.eureka2.codec;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.netflix.eureka2.spi.protocol.ProtocolMessage;

/**
 * @author Tomasz Bak
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = As.PROPERTY, property = "class")
public class SampleObject implements ProtocolMessage {

    public static final SampleObject CONTENT;

    static {
        Map<String, String> mapValue = new HashMap<>();
        mapValue.put("keyA", "valueA");
        CONTENT = new SampleObject(new Internal("stringValue", mapValue));
    }

    private Internal[] internals;

    public SampleObject() {
    }

    public SampleObject(Internal... internals) {
        this.internals = internals;
    }

    public Internal[] getInternal() {
        return internals;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SampleObject that = (SampleObject) o;

        if (!Arrays.equals(internals, that.internals)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return internals != null ? Arrays.hashCode(internals) : 0;
    }

    @Override
    public String toString() {
        return "SampleObject{internals=" + Arrays.toString(internals) + '}';
    }

    public static class Internal {
        private String value;

        private Map<String, String> mapValue;

        public Internal() {
        }

        public Internal(String value) {
            this.value = value;
            this.mapValue = Collections.emptyMap();
        }

        public Internal(Map<String, String> mapValue) {
            this.mapValue = mapValue;
        }

        public Internal(String value, Map<String, String> mapValue) {
            this.value = value;
            this.mapValue = mapValue;
        }

        public String getValue() {
            return value;
        }

        public Map<String, String> getMapValue() {
            return mapValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Internal internal = (Internal) o;

            if (mapValue != null ? !mapValue.equals(internal.mapValue) : internal.mapValue != null) {
                return false;
            }
            if (value != null ? !value.equals(internal.value) : internal.value != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = value != null ? value.hashCode() : 0;
            result = 31 * result + (mapValue != null ? mapValue.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Internal{value='" + value + '\'' + ", mapValue=" + mapValue + '}';
        }
    }
}
