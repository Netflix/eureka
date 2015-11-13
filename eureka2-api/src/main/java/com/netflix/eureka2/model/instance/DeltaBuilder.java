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

package com.netflix.eureka2.model.instance;

/**
 */
public abstract class DeltaBuilder {

    protected String id;
    protected InstanceInfoField<?> field;
    protected Object value;

    public DeltaBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public <T> DeltaBuilder withDelta(InstanceInfoField<T> field, T value) {
        this.field = field;
        this.value = value;
        return this;
    }

    public abstract Delta build();
}
