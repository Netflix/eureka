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
import java.util.Set;

import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.InstanceInfoField;
import com.netflix.eureka.registry.InstanceInfoField.Name;

/**
 * @author Tomasz Bak
 */
public class UpdateInstanceInfo<T> implements InterestSetNotification {

    private final DeltaDTO<?> deltaDTO;

    // For serialization framework
    protected UpdateInstanceInfo() {
        deltaDTO = null;
    }

    public UpdateInstanceInfo(Delta<?> delta) {
        deltaDTO = toDeltaDTO(delta);
    }

    public Delta<?> getDelta() {
        return deltaDTO.toDelta();
    }

    @SuppressWarnings("rawtypes")
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    static DeltaDTO<?> toDeltaDTO(Delta<?> delta) {
        Type type = delta.getField().getValueType();
        if (type instanceof Class) {
            Class<?> ctype = (Class<?>) type;
            if (ctype.equals(String.class)) {
                return new StringDeltaDTO((Delta<String>) delta);
            } else if (Enum.class.isAssignableFrom(ctype)) {
                return new EnumDeltaDTO((Delta<Enum>) delta);
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType) type;
            if (Set.class.isAssignableFrom((Class<?>) ptype.getRawType())) {
                Type targ = ptype.getActualTypeArguments()[0];
                if (Integer.class.equals(targ)) {
                    return new SetIntDeltaDTO((Delta<Set<Integer>>) delta);
                } else if (String.class.equals(targ)) {
                    return new SetStringDeltaDTO((Delta<Set<String>>) delta);
                }
            }
        }
        throw new IllegalArgumentException("Unexpected delta type " + type);
    }

    abstract static class DeltaDTO<T> {
        protected final String id;
        protected final long version;
        protected final String fieldName;

        protected DeltaDTO() {
            id = null;
            version = -1;
            fieldName = null;
        }

        protected DeltaDTO(Delta<T> delta) {
            this.id = delta.getId();
            this.version = delta.getVersion();
            this.fieldName = delta.getField().getFieldName().name();
        }

        public abstract T getValue();

        @SuppressWarnings("rawtypes")
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
            Object thisValue = getValue();
            Object thatValue = deltaDTO.getValue();
            if (thisValue != null ? !thisValue.equals(thatValue) : thatValue != null) {
                return false;
            }
            return true;
        }

        @SuppressWarnings("unchecked")
        public Delta<T> toDelta() {
            return (Delta<T>) new Delta.Builder()
                    .withId(id)
                    .withVersion(version)
                    .withDelta(InstanceInfoField.forName(Name.forName(fieldName)), getValue())
                    .build();
        }

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (int) (version ^ (version >>> 32));
            result = 31 * result + (fieldName != null ? fieldName.hashCode() : 0);
            Object value = getValue();
            result = 31 * result + (value != null ? value.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "DeltaDTO{" +
                    "id='" + id + '\'' +
                    ", version=" + version +
                    ", fieldName='" + fieldName + '\'' +
                    ", value=" + getValue() +
                    '}';
        }
    }

    public static class StringDeltaDTO extends DeltaDTO<String> {
        private String value;

        public StringDeltaDTO() {
        }

        public StringDeltaDTO(Delta<String> delta) {
            super(delta);
            value = delta.getValue();
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    @SuppressWarnings("rawtypes")
    public static class EnumDeltaDTO extends DeltaDTO<Enum> {
        Enum value;

        public EnumDeltaDTO() {
        }

        public EnumDeltaDTO(Delta<Enum> delta) {
            super(delta);
            value = delta.getValue();
        }

        @Override
        public Enum getValue() {
            return value;
        }
    }

    public static class SetIntDeltaDTO extends DeltaDTO<Set<Integer>> {
        Set<Integer> value;

        public SetIntDeltaDTO() {
        }

        public SetIntDeltaDTO(Delta<Set<Integer>> delta) {
            super(delta);
            value = delta.getValue();
        }

        @Override
        public Set<Integer> getValue() {
            return value;
        }
    }

    public static class SetStringDeltaDTO extends DeltaDTO<Set<String>> {
        Set<String> value;

        public SetStringDeltaDTO() {
        }

        public SetStringDeltaDTO(Delta<Set<String>> delta) {
            super(delta);
            value = delta.getValue();
        }

        @Override
        public Set<String> getValue() {
            return value;
        }
    }
}
