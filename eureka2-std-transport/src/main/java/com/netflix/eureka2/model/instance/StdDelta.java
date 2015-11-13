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

package com.netflix.eureka2.model.instance;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.netflix.eureka2.codec.jackson.DeltaDeserializer;
import com.netflix.eureka2.codec.jackson.DeltaSerializer;

/**
 * A matching pair of field:value that denotes a delta change to an InstanceInfo
 * Deltas must also contain an id denoting which InstanceInfo id it correspond to.
 *
 * @author David Liu
 */
@JsonSerialize(using = DeltaSerializer.class)
@JsonDeserialize(using = DeltaDeserializer.class)
public class StdDelta<ValueType> implements Delta<ValueType> {

    private String id;

    private InstanceInfoField<ValueType> field;
    private ValueType value;

    private StdDelta() {
    } // for serializer

    private StdDelta(String id, InstanceInfoField<ValueType> field, ValueType value) {
        this.id = id;
        this.field = field;
        this.value = value;
    }

    @Override
    public InstanceInfoBuilder applyTo(InstanceInfoBuilder instanceInfoBuilder) {
        return field.update(instanceInfoBuilder, value);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public InstanceInfoField<ValueType> getField() {
        return field;
    }

    @Override
    public ValueType getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StdDelta)) {
            return false;
        }

        StdDelta delta = (StdDelta) o;

        if (field != null ? !field.equals(delta.field) : delta.field != null) {
            return false;
        }
        if (id != null ? !id.equals(delta.id) : delta.id != null) {
            return false;
        }
        if (value != null ? !value.equals(delta.value) : delta.value != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (field != null ? field.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Delta{" +
                "id='" + id + '\'' +
                ", field=" + field +
                ", value=" + value +
                '}';
    }

    public static final class Builder extends DeltaBuilder {
        @SuppressWarnings("unchecked")
        public StdDelta<?> build() {
            if (id == null || field == null) {  // null data.value is ok
                throw new IllegalStateException("Incomplete delta information");
            }
            return new StdDelta(id, field, value);
        }
    }
}
