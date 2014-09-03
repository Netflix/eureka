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

package com.netflix.eureka.protocol.discovery;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;

import com.netflix.eureka.protocol.discovery.TypeWrapper.HashSetInt;
import com.netflix.eureka.protocol.discovery.TypeWrapper.HashSetString;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.InstanceInfoField;
import com.netflix.eureka.registry.InstanceInfoField.Name;

/**
 * @author Tomasz Bak
 */
public class UpdateInstanceInfo implements InterestSetNotification {

    private final DeltaDTO deltaDTO;

    // For serialization framework
    protected UpdateInstanceInfo() {
        deltaDTO = null;
    }

    public UpdateInstanceInfo(Delta<?> delta) {
        this.deltaDTO = new DeltaDTO(delta);
    }

    public Delta<?> getDelta() {
        return deltaDTO.toDelta();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UpdateInstanceInfo that = (UpdateInstanceInfo) o;

        if (deltaDTO != null ? !deltaDTO.equals(that.deltaDTO) : that.deltaDTO != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return deltaDTO != null ? deltaDTO.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "UpdateInstanceInfo{deltaDTO=" + deltaDTO + '}';
    }

    public static class DeltaDTO {
        private final String id;
        private final long version;
        private final String fieldName;
        private final Object value;

        public DeltaDTO() {
            id = null;
            version = -1;
            fieldName = null;
            value = null;
        }

        public DeltaDTO(String id, long version, String fieldName, Object value) {
            this.id = id;
            this.version = version;
            this.fieldName = fieldName;
            this.value = value;
        }

        public DeltaDTO(Delta<?> delta) {
            this.id = delta.getId();
            this.version = delta.getVersion();
            this.fieldName = delta.getField().getFieldName().name();
            this.value = toDtoValue(delta.getField().getValueType(), delta.getValue());
        }

        private Object toDtoValue(Type type, Object value) {
            if (type instanceof ParameterizedType) {
                ParameterizedType ptype = (ParameterizedType) type;
                Type[] types = ptype.getActualTypeArguments();
                if (types[0].equals(String.class)) {
                    return new HashSetString((HashSet<String>) value);
                } else if (types[0].equals(Integer.class)) {
                    return new HashSetInt((HashSet<Integer>) value);
                } else {
                    throw new RuntimeException("Unsupported type");
                }
            }
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DeltaDTO deltaDTO = (DeltaDTO) o;

            if (version != deltaDTO.version) {
                return false;
            }
            if (fieldName != null ? !fieldName.equals(deltaDTO.fieldName) : deltaDTO.fieldName != null) {
                return false;
            }
            if (id != null ? !id.equals(deltaDTO.id) : deltaDTO.id != null) {
                return false;
            }
            if (value != null ? !value.equals(deltaDTO.value) : deltaDTO.value != null) {
                return false;
            }

            return true;
        }

        public Delta toDelta() {
            Object unwrapped = value instanceof TypeWrapper ? ((TypeWrapper) value).getValue() : value;
            return new Delta.Builder()
                    .withId(id)
                    .withVersion(version)
                    .withDelta(InstanceInfoField.forName(Name.forName(fieldName)), unwrapped)
                    .build();
        }

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (int) (version ^ (version >>> 32));
            result = 31 * result + (fieldName != null ? fieldName.hashCode() : 0);
            result = 31 * result + (value != null ? value.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "DeltaDTO{" +
                    "id='" + id + '\'' +
                    ", version=" + version +
                    ", fieldName='" + fieldName + '\'' +
                    ", value=" + value +
                    '}';
        }
    }
}
