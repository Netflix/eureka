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

    private InstanceInfoField<ValueType> field;
    private ValueType value;

    private Delta()  {} // for serializer

    InstanceInfo.Builder applyTo(InstanceInfo.Builder instanceInfoBuilder) {
        return field.update(instanceInfoBuilder, value);
    }

    public String getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public InstanceInfoField<ValueType> getField() {
        return field;
    }

    public ValueType getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Delta)) {
            return false;
        }

        Delta delta = (Delta) o;

        if (field != null ? !field.equals(delta.field) : delta.field != null) {
            return false;
        }
        if (id != null ? !id.equals(delta.id) : delta.id != null) {
            return false;
        }
        if (value != null ? !value.equals(delta.value) : delta.value != null) {
            return false;
        }
        if (version != null ? !version.equals(delta.version) : delta.version != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (field != null ? field.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Delta{" + "id='" + id + '\'' + ", version=" + version + ", field=" + field + ", value=" + value + '}';
    }

    //TODO (nkant): Is this builder required?
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

        public <T> Builder withDelta(InstanceInfoField<T> field, T value) {
            Delta<T> delta = new Delta<T>();
            delta.field = field;
            delta.value = value;
            this.delta = delta;
            return this;
        }

        public Delta<?> build() {
            delta.id = this.id;
            delta.version = this.version;
            if (delta.id == null || delta.version == null
                    || delta.field == null || delta.value == null) {
                throw new IllegalStateException("Incomplete delta information");
            }

            return delta;
        }
    }
}
