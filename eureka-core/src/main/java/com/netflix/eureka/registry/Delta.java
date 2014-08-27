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

package com.netflix.eureka.registry;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;

/**
 * A matching pair of field:value that denotes a delta change to an InstanceInfo
 * Deltas must also contain an id denoting which InstanceInfo id it correspond to,
 * as well as a version string for the instance to update to.
 *
 * @author David Liu
 */
public class Delta<ValueType> {

    private String id;
    private Long version;

    private String fieldName;
    private ValueType value;

    private Delta()  {} // for serializer

    InstanceInfo.Builder applyTo(InstanceInfo.Builder instanceInfoBuilder) throws Exception {
        if (value instanceof TypeWrapper) { // TODO: remove once we move off avro
            return InstanceInfoField.applyTo(instanceInfoBuilder, fieldName, ((TypeWrapper) value).getValue());
        } else {
            return InstanceInfoField.applyTo(instanceInfoBuilder, fieldName, value);
        }
    }

    public String getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Delta)) return false;

        Delta delta = (Delta) o;

        if (fieldName != null ? !fieldName.equals(delta.fieldName) : delta.fieldName != null) return false;
        if (id != null ? !id.equals(delta.id) : delta.id != null) return false;
        if (value != null ? !value.equals(delta.value) : delta.value != null) return false;
        if (version != null ? !version.equals(delta.version) : delta.version != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (fieldName != null ? fieldName.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Delta{" +
                "id='" + id + '\'' +
                ", version=" + version +
                ", fieldName='" + fieldName + '\'' +
                ", value=" + value +
                '}';
    }

    public static final class Builder {
        private String id;
        private Long version;
        Delta<?> delta;

        public Builder() {
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withVersion(Long version) {
            this.version = version;
            return this;
        }

        public <T> Builder withDelta(InstanceInfoField<T> field, T value) throws Exception {
             Delta<T> delta = new Delta<T>();
             delta.fieldName = field.getFieldName();
             delta.value = value;
             this.delta = delta;
            return this;
        }

        // Special version to support hashsets with different parameterized types.
        // This is needed to support avro serialization.
        // TODO: Once/when we move off avro, revisit this and remove if possible
        @SuppressWarnings("unchecked")
        public <ValueType> Builder withDelta(InstanceInfoField<HashSet<ValueType>> field, HashSet<ValueType> value) {
            if (field.getValueType() instanceof ParameterizedType) {
                ParameterizedType ptype = (ParameterizedType)field.getValueType();
                Type[] types = ptype.getActualTypeArguments();
                if (types[0].equals(String.class)) {
                    Delta<TypeWrapper.HashSetString> delta = new Delta<TypeWrapper.HashSetString>();
                    delta.fieldName = field.getFieldName();
                    delta.value = new TypeWrapper.HashSetString((HashSet<String>)value);
                    this.delta = delta;
                } else if (types[0].equals(Integer.class)) {
                    Delta<TypeWrapper.HashSetInt> delta = new Delta<TypeWrapper.HashSetInt>();
                    delta.fieldName = field.getFieldName();
                    delta.value = new TypeWrapper.HashSetInt((HashSet<Integer>)value);
                    this.delta = delta;
                } else {
                    throw new RuntimeException("Unsupported type");
                }
            }
            return this;
        }

        public Delta<?> build() {
            delta.id = this.id;
            delta.version = this.version;
            if (delta.id == null || delta.version == null
                    || delta.fieldName == null || delta.value == null) {
                throw new IllegalStateException("Incomplete delta information");
            }

            return delta;
        }
    }
}
